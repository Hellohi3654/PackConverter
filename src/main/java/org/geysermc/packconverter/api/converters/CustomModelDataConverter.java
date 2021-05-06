/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 *  @author GeyserMC
 *  @link https://github.com/GeyserMC/PackConverter
 *
 */

package org.geysermc.packconverter.api.converters;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import org.geysermc.packconverter.api.PackConverter;
import org.geysermc.packconverter.api.utils.CustomModelData;
import org.geysermc.packconverter.api.utils.CustomModelDataHandler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class CustomModelDataConverter extends AbstractConverter {

    @Getter
    public static final List<Object[]> defaultData = new ArrayList<>();

    static {
        defaultData.add(new String[] {"assets/minecraft/models/item", "textures/item_texture.json"});
    }

    public CustomModelDataConverter(PackConverter packConverter, Path storage, Object[] data) {
        super(packConverter, storage, data);
    }

    @Override
    public List<AbstractConverter> convert() {
        packConverter.log("Checking for custom model data");
        try {
            String from = (String) this.data[0];
            String to = (String) this.data[1];

            ObjectMapper mapper = new ObjectMapper();

            JsonNode itemInformation;
            try {
                InputStream stream = PackConverter.class.getResourceAsStream("/item_information.json");
                itemInformation = mapper.readTree(stream);
            } catch (IOException e) {
                e.printStackTrace();
                return new ArrayList<>();
            }

            // Create the texture_data file that will map all textures
            ObjectNode textureData = mapper.createObjectNode();
            textureData.put("resource_pack_name", "geysercmd");
            textureData.put("texture_name", "atlas.items");
            ObjectNode allTextures = mapper.createObjectNode();
            handleCustomModelData(itemInformation, allTextures, mapper, storage.resolve(from).toFile());

            textureData.set("texture_data", allTextures);

            if (!packConverter.getCustomModelData().isEmpty()) {
                // We have custom model data, so let's write the textures
                OutputStream outputStream = Files.newOutputStream(storage.resolve(to), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                mapper.writer(new DefaultPrettyPrinter()).writeValue(outputStream, textureData);
            }
            packConverter.log(String.format("Converted models %s", from));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }

    private void handleCustomModelData(JsonNode itemInformation, ObjectNode allTextures, ObjectMapper mapper, File directory) {
        for (File file : directory.listFiles()) {
            try {
                if (!file.isFile()) {
                    handleCustomModelData(itemInformation, allTextures, mapper, file);
                }
                InputStream stream = new FileInputStream(file);

                JsonNode node = mapper.readTree(stream);
                if (node.has("overrides")) {
                    String originalItemName = file.getName().replace(".json", "");
                    JsonNode itemJsonInfo = itemInformation.get(originalItemName);
                    if (itemJsonInfo == null) {
                        System.out.println("No item information for " + file.getName().replace(".json", ""));
                        return;
                    }
                    for (JsonNode override : node.get("overrides")) {
                        JsonNode predicate = override.get("predicate");
                        JsonNode pulling = predicate.get("pulling");
                        //if (pulling != null && pulling.asInt() != 0) {
                        if (pulling != null) { //FIXME: Don't translate bows or they are otherwise non-functional for Bedrock
                            // Animation for bow handled for the core bow item
                            continue;
                        }
                        // This is where the custom model data happens - each one is registered here under "predicate"
                        if (predicate.has("custom_model_data")) {
                            String filePath = override.get("model").asText();
                            if (filePath.startsWith("minecraft:")) {
                                // We don't need this lol
                                continue;
                            }
                            // The "ID" of the CustomModelData. If the ID is 1, then to get the custom model data
                            // You need to run in Java `/give @s stick{CustomModelData:1}`
                            int id = predicate.get("custom_model_data").asInt();
                            // Get the identifier that we'll register the item with on Bedrock, and create the JSON file
                            CustomModelData customModelData = CustomModelDataHandler.handleItemData(mapper, storage, originalItemName, filePath, itemJsonInfo, predicate);
                            // See if we have registered the vanilla item already
                            Int2ObjectMap<CustomModelData> data = packConverter.getCustomModelData().getOrDefault(originalItemName, null);
                            //packConverter.getBehaviorPack().writeBehaviorPackItem(mapper, filePath, itemJsonInfo);
                            if (data == null) {
                                // Create a fresh map of Java CustomModelData IDs to Bedrock string identifiers
                                Int2ObjectMap<CustomModelData> map = new Int2ObjectOpenHashMap<>();
                                map.put(id, customModelData);
                                // Put the vanilla item (stick) and the initialized map in the custom model data table
                                packConverter.getCustomModelData().put(file.getName().replace(".json", ""), map);
                            } else {
                                // Map exists, add the new CustomModelData ID and Bedrock string identifier
                                data.put(id, customModelData);
                            }

                            // Create the texture information
                            ObjectNode textureInfo = CustomModelDataHandler.handleItemTexture(mapper, storage, filePath);
                            if (textureInfo != null) {
                                // If texture was created, add it to the file where Bedrock will read all textures
                                allTextures.setAll(textureInfo);
                            } else {
                                System.out.println("No texture for " + filePath);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
