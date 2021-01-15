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

package org.geysermc.packconverter.api.utils;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtMapBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class CustomModelDataHandler {

    public static CustomModelData handleItemData(ObjectMapper mapper, Path storage, String filePath, JsonNode itemJsonInfo, JsonNode predicate) {
        // Start the creation of the JSON that registers the object
        ObjectNode item = mapper.createObjectNode();
        // Standard JSON
        item.put("format_version", "1.16.0");
        ObjectNode itemData = mapper.createObjectNode();
        ObjectNode itemDescription = mapper.createObjectNode();

        // Full identifier with geysercmd prefix (cmd for CustomModelData - just in case it clashes with something we do in the future)
        String identifier = "geysercmd:" + filePath.substring(filePath.lastIndexOf("/") + 1);
        // Register the full identifier
        itemDescription.put("identifier", identifier);
        itemData.set("description", itemDescription);
        NbtMapBuilder componentBuilder = NbtMap.builder();
        NbtMapBuilder itemPropertiesBuilder = NbtMap.builder();
        itemPropertiesBuilder.putBoolean("allow_off_hand", true); // We always want offhand to be accessible
        itemPropertiesBuilder.putBoolean("hand_equipped", itemJsonInfo.get("hand_equipped").booleanValue());
        itemPropertiesBuilder.putInt("max_stack_size", itemJsonInfo.get("max_stack_size").intValue());

        componentBuilder.putCompound("item_properties", itemPropertiesBuilder.build());
        item.set("minecraft:item", itemData);

        JsonNode pulling = predicate.get("pulling");
        if (pulling != null) {
            //itemPropertiesBuilder.putInt("use_animation", 1);
            componentBuilder.putString("minecraft:render_offsets", "miscellaneous");
            componentBuilder.putString("minecraft:use_animation", "bow");
        }

        int maxDamage = itemJsonInfo.get("max_damage").asInt();
        if (maxDamage != 0) {
            componentBuilder.putCompound("minecraft:durability", NbtMap.builder().putInt("max_durability", maxDamage).build());
        }

        componentBuilder.putCompound("minecraft:icon", NbtMap.builder().putString("texture", identifier.replace("geysercmd:", "")).build());

        ObjectNode itemComponent = mapper.createObjectNode();
        // Define which texture in item_texture.json this should use. We just set it to the "clean identifier"
        itemComponent.put("minecraft:icon", identifier.replace("geysercmd:", ""));
        itemData.set("components", itemComponent);

        // Create, if necessary, the folder that stores all item information
        File itemJsonPath = storage.resolve("items").toFile();
        if (!itemJsonPath.exists()) {
            itemJsonPath.mkdir();
        }

        // Write our item information
        Path path = itemJsonPath.toPath().resolve(filePath.substring(filePath.lastIndexOf("/") + 1) + ".json");
        try (OutputStream outputStream = Files.newOutputStream(path,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            mapper.writer(new DefaultPrettyPrinter()).writeValue(outputStream, item);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        CustomModelData customModelData = new CustomModelData();
        customModelData.setIdentifier(identifier);
        customModelData.setNbt(componentBuilder.build());

        return customModelData;
    }

    private static NbtMap buildCompoundValue(Object value) {
        NbtMapBuilder builder = NbtMap.builder();
        builder.put("value", value);
        return builder.build();
    }

    public static ObjectNode handleItemTexture(ObjectMapper mapper, Path storage, String filePath) {
        String cleanIdentifier = filePath.substring(filePath.lastIndexOf("/") + 1);

        JsonNode textureFile;
        File textureFilePath;
        if (filePath.contains(":")) {
            String[] namespaceSplit = filePath.split(":");
            textureFilePath = storage.resolve("assets/" + namespaceSplit[0] + "/models/" + namespaceSplit[1] + ".json").toFile();
        } else {
            textureFilePath = storage.resolve("assets/minecraft/models/" + filePath + ".json").toFile();
        }
        if (!textureFilePath.exists()) {
            System.out.println("No texture file found at " + textureFilePath + "; we were given " + filePath);
            return null;
        }
        try (InputStream stream = new FileInputStream(textureFilePath)) {
            // Read the model information for the Java CustomModelData
            textureFile = mapper.readTree(stream);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        // TODO: This is called BSing it. It works but is it correct?
        if (textureFile.has("textures")) {
            if (textureFile.get("textures").has("0") || textureFile.get("textures").has("layer0")) {
                String determine = textureFile.get("textures").has("0") ? "0" : "layer0";
                ObjectNode textureData = mapper.createObjectNode();
                ObjectNode textureName = mapper.createObjectNode();
                // Make JSON data for Bedrock pointing to where texture data for this item is stored
                String textureString = textureFile.get("textures").get(determine).textValue();
                if (textureString.contains(":")) {
                    String[] namespaceSplit = textureString.split(":");
                    String texturePath = "assets/" + namespaceSplit[0] + "/textures/" + namespaceSplit[1];
                    String restOfTheTexturePath = namespaceSplit[1].substring(0, namespaceSplit[1].lastIndexOf("/"));
                    if (!namespaceSplit[0].equals("minecraft")) {
                        File namespaceFile = storage.resolve("textures/" + namespaceSplit[0] + "/" + restOfTheTexturePath).toFile();
                        if (!namespaceFile.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            namespaceFile.mkdirs();
                        }
                        try {
                            // Copy from the original location to a new place in the resource pack
                            // For example: /assets/itemsadder/textures/item/crystal.png to textures/itemsadder/item/crystal.png
                            Files.copy(storage.resolve(texturePath + ".png"), namespaceFile.toPath().resolve(namespaceSplit[1].substring(namespaceSplit[1].lastIndexOf("/") + 1) + ".png"));
                            textureName.put("textures", "textures/" + namespaceSplit[0] + "/" +  namespaceSplit[1]);
                            // Have the identifier point to that texture data
                            textureData.set(cleanIdentifier, textureName);
                            return textureData;
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    } else {
                        return null;
                    }
                }

                if (textureString.startsWith("item/")) {
                    textureString = textureString.replace("item/", "textures/items/");
                } else {
                    textureString = "textures/" + textureString;
                }
                textureName.put("textures", textureString);
                // Have the identifier point to that texture data
                textureData.set(cleanIdentifier, textureName);
                return textureData;
            }
        }

        return null;
    }

}
