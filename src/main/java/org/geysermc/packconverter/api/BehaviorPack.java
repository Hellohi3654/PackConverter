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

package org.geysermc.packconverter.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import org.geysermc.packconverter.api.utils.ResourcePackManifest;
import org.geysermc.packconverter.api.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class BehaviorPack {

    private final PackConverter packConverter;

    @Getter
    private boolean enabled;

    /**
     * Information about the resource pack to set as a dependency
     */
    @Setter
    private ResourcePackManifest.Header resourcePackInfo;

    public BehaviorPack(PackConverter packConverter) {
        this.packConverter = packConverter;
    }

    public void enable() {
        packConverter.log("Create behavior pack");

        Path packPath = packConverter.getTmpDir().resolve("behavior");
        //noinspection ResultOfMethodCallIgnored
        packPath.toFile().mkdir();

        ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.ALLOW_COMMENTS);

        ResourcePackManifest.Header header = new ResourcePackManifest.Header();
        header.setName("PackConverter behavior pack");
        header.setDescription("Converted behavior pack to support the resource pack");
        header.setUuid(UUID.randomUUID());
        header.setVersion(new int[] { 1, 0, 0});
        header.setMinimumSupportedMinecraftVersion(new int[] {1, 14, 0});

        ResourcePackManifest.Module module = new ResourcePackManifest.Module();
        module.setDescription("Converted behavior pack to support the resource pack");
        module.setType("data");
        module.setUuid(UUID.randomUUID());
        module.setVersion(new int[] { 1, 0, 0});

        ResourcePackManifest manifest = new ResourcePackManifest();
        manifest.setFormatVersion(2);
        manifest.setHeader(header);
        Collection<ResourcePackManifest.Module> modules = new ArrayList<>();
        modules.add(module);
        manifest.setModules(modules);

        ResourcePackManifest.Dependency dependency = new ResourcePackManifest.Dependency();
        dependency.setUuid(resourcePackInfo.getUuid());
        dependency.setVersion(resourcePackInfo.getVersion());
        Collection<ResourcePackManifest.Dependency> dependencies = new ArrayList<>();
        dependencies.add(dependency);
        manifest.setDependencies(dependencies);

        try {
            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
            writer.writeValue(packPath.resolve("manifest.json").toFile(), manifest);
            Files.copy(packConverter.getTmpDir().resolve("resources").resolve("pack_icon.png"), packPath.resolve("pack_icon.png"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.enabled = true;
    }

    public void pack() {
        ZipUtils zipUtils = new ZipUtils(packConverter, packConverter.getTmpDir().resolve("behavior").toFile());
        zipUtils.generateFileList();
        zipUtils.zipIt(packConverter.getOutput().getParent().resolve("bp_" + packConverter.getOutput().toFile().getName()).toString());
    }

    public void writeBehaviorPackItem(ObjectMapper mapper, String filePath, JsonNode itemJsonInfo) {
        if (!this.enabled) {
            throw new RuntimeException("Behavior pack not enabled but trying to write item!");
        }
        // Start the creation of the JSON that registers the object
        ObjectNode item = mapper.createObjectNode();
        // Standard JSON
        item.put("format_version", "1.16.0");
        ObjectNode itemData = mapper.createObjectNode();
        ObjectNode itemDescription = mapper.createObjectNode();

        // Full identifier with geysercmd prefix (cmd for CustomModelData - just in case it clashes with something we do in the future)
        String identifier = "geysercmd:" + filePath.replace("item/", "");
        // Register the full identifier
        itemDescription.put("identifier", identifier);
        itemData.set("description", itemDescription);
        ObjectNode itemComponent = mapper.createObjectNode();
        item.set("minecraft:item", itemData);

        // TODO: 1.16.100 is adding offhand component. Always add that.
        itemComponent.put("minecraft:hand_equipped", itemJsonInfo.get("hand_equipped").booleanValue());
        itemComponent.put("minecraft:max_stack_size", itemJsonInfo.get("max_stack_size").intValue());
        int maxDamage = itemJsonInfo.get("max_damage").asInt();
        if (maxDamage != 0) {
            itemComponent.put("minecraft:max_damage", maxDamage);
        }
        itemData.set("components", itemComponent);

        // Create, if necessary, the folder that stores all item information
        File itemJsonPath = packConverter.getTmpDir().resolve("behavior").resolve("items").toFile();
        if (!itemJsonPath.exists()) {
            itemJsonPath.mkdir();
        }

        // Write our item information
        Path path = itemJsonPath.toPath().resolve(filePath.replace("item/", "") + ".json");
        try (OutputStream outputStream = Files.newOutputStream(path,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            mapper.writer(new DefaultPrettyPrinter()).writeValue(outputStream, item);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
