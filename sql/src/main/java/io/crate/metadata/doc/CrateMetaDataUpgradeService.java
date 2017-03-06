/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.metadata.doc;

import io.crate.Constants;
import org.elasticsearch.cluster.metadata.CustomUpgradeService;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.MapBuilder;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Singleton;
import org.elasticsearch.common.settings.Settings;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class CrateMetaDataUpgradeService extends AbstractComponent implements CustomUpgradeService {

    @Inject
    public CrateMetaDataUpgradeService(Settings settings) {
        super(settings);
    }

    @Override
    public IndexMetaData upgradeIndexMetaData(IndexMetaData indexMetaData) {
        try {
            logger.trace("upgrading index={}", indexMetaData.getIndex());
            IndexMetaData newIndexMetaData = saveRoutingHashFunctionToMapping(indexMetaData);
            return newIndexMetaData;
        } catch (Exception e) {
            logger.error("index={} could not be upgraded", indexMetaData.getIndex());
            throw new RuntimeException(e);
        }
    }

    private static IndexMetaData saveRoutingHashFunctionToMapping(IndexMetaData indexMetaData) throws IOException {
        Map<String, Object> mappingMap = DocIndexMetaData.getMappingMap(indexMetaData);
        assert mappingMap != null : "Mapping metadata of index: " + indexMetaData.getIndex() + " is empty";
        String hashFunction = DocIndexMetaData.getRoutingHashFunctionType(mappingMap);
        if (hashFunction == null) {
            String routingHashFunctionType = indexMetaData.getSettings().get(IndexMetaData.SETTING_LEGACY_ROUTING_HASH_FUNCTION);
            if (routingHashFunctionType == null) {
                routingHashFunctionType = DocIndexMetaData.DEFAULT_ROUTING_HASH_FUNCTION;
            }
            // create new map, existing one can be immutable
            Map<String, Object> newMappingMap = MapBuilder.<String, Object>newMapBuilder().putAll(mappingMap).map();

            Map<String, Object> metaMap = (Map) newMappingMap.get("_meta");
            if (metaMap == null) {
                metaMap = new HashMap<>(1);
                newMappingMap.put("_meta", metaMap);
            }
            metaMap.put(DocIndexMetaData.SETTING_ROUTING_HASH_FUNCTION, routingHashFunctionType);

            Map<String, Object> typeAndMapping = new HashMap(1);
            typeAndMapping.put(Constants.DEFAULT_MAPPING_TYPE, newMappingMap);
            return IndexMetaData.builder(indexMetaData)
                .removeMapping(Constants.DEFAULT_MAPPING_TYPE)
                .putMapping(new MappingMetaData(typeAndMapping))
                .build();
        }
        return indexMetaData;
    }

}
