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

package io.crate.metadata;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import com.google.common.annotations.VisibleForTesting;
import io.crate.Constants;
import io.crate.metadata.doc.DocIndexMetaData;
import io.crate.operation.reference.sys.check.cluster.TablesNeedUpgradeSysCheck;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Singleton;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_LEGACY_ROUTING_HASH_FUNCTION;

@Singleton
public class IndexVersionListener extends AbstractLifecycleComponent<IndexVersionListener> implements ClusterStateListener {


    private final ClusterService clusterService;
    private final ESLogger logger;
    private boolean alreadyRun = false;

    @Inject
    public IndexVersionListener(Settings settings, ClusterService clusterService) {
        super(settings);
        this.clusterService = clusterService;
        this.logger = Loggers.getLogger(TablesNeedUpgradeSysCheck.class, settings);
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        if (!alreadyRun && event.localNodeMaster() && !event.state().metaData().indices().isEmpty()) {
            Map<String, IndexMetaData> indexMetaDataToUpdate = new HashMap<>();
            for (Iterator<ObjectObjectCursor<String, IndexMetaData>> iterator =
                 clusterService.state().metaData().indices().iterator(); iterator.hasNext();) {
                ObjectObjectCursor<String, IndexMetaData> cursor = iterator.next();
                String indexName = cursor.key;
                IndexMetaData indexMetaData = cursor.value;
                MappingMetaData mappingMetaData = DocIndexMetaData.getMappingMetaData(indexMetaData);
                assert mappingMetaData != null : "Mapping metadata of index: " + indexName + " is empty";
                    try {
                        String hashFunction = DocIndexMetaData.getRoutingHashFunction(mappingMetaData.sourceAsMap());
                        if (hashFunction == null) {
                            updateIndexRoutingHashFunction(indexName, indexMetaData, indexMetaDataToUpdate);
                        }
                    } catch (IOException e) {
                        logger.error("unable to read routing hash function for index: {}", cursor.index, e);
                    }
                }
            }
            alreadyRun = true;
        }
    }

    @Override
    protected void doStart() {
        clusterService.add(this);
    }

    @Override
    protected void doStop() {
        clusterService.remove(this);
    }

    @Override
    protected void doClose() {
    }

//    clusterService.submitStateUpdateTask("update-index-routing-hash-function", new ClusterStateUpdateTask() {
//        @Override
//        public ClusterState execute(ClusterState currentState) throws Exception {
//            return addDefaultTemplate(currentState);
//        }
//
//        @Override
//        public void onFailure(String source, Throwable cause) {
//            logger.error("Error during ensure-default-template source={}", cause, source);
//        }
//    });

    private void updateIndexRoutingHashFunction(String indexName,
                                                IndexMetaData indexMetaData,
                                                Map<String, IndexMetaData> indexMetaDataToUpdate) throws IOException {
        String routingHashAlgorithm = indexMetaData.getSettings().get(SETTING_LEGACY_ROUTING_HASH_FUNCTION);
        if (routingHashAlgorithm == null) {
            routingHashAlgorithm = "Murmur3HashFunction";
        }
        MappingMetaData mappingMetaData = DocIndexMetaData.getMappingMetaData(indexMetaData);
        HashMap<String, Object> newMappingMetaData = new HashMap(mappingMetaData.sourceAsMap());
        newMappingMetaData.put("routing_hash_algorithm", routingHashAlgorithm);
        indexMetaDataToUpdate.put(
            indexName,
            new IndexMetaData.Builder(indexMetaData).putMapping(new MappingMetaData(newMappingMetaData)).build());
    }

//    @VisibleForTesting
//    static ClusterState addDefaultTemplate(ClusterState currentState, String indexName) throws IOException {
//        currentState.metaData().
//
//        MetaData currentMetaData = currentState.getMetaData();
//        String hashFunction = currentMetaData.index(indexName).getSettings().get(SETTING_LEGACY_ROUTING_HASH_FUNCTION);
//        if (hashFunction == null) {
//            MetaData.Builder mdBuilder = MetaData.builder(currentMetaData).indices()
//            ClusterState.builder(currentState).me
//        }
//        ImmutableOpenMap<String, IndexTemplateMetaData> currentTemplates = currentMetaData.getTemplates();
//        ImmutableOpenMap<String, IndexMetaData> newTemplates = createCopyWithHashFunctionAdded(currentMetaData.indices());
//        MetaData.Builder mdBuilder = MetaData.builder(currentMetaData).templates(newTemplates);
//        return ClusterState.builder(currentState).metaData(mdBuilder).build();
//    }
//
//    private static ImmutableOpenMap<String, IndexTemplateMetaData> createCopyWithHashFunctionAdded(
//        ImmutableOpenMap<String, IndexMetaData> currentIndices, String index) throws IOException {
//        IndexMetaData.builder(currentIndices.get(index));
//
//
//        IndexTemplateMetaData defaultTemplate = IndexTemplateMetaData.builder(TEMPLATE_NAME)
//            .order(0)
//            .putMapping(Constants.DEFAULT_MAPPING_TYPE, DEFAULT_MAPPING_SOURCE)
//            .template("*")
//            .build();
//        ImmutableOpenMap.Builder<String, IndexTemplateMetaData> builder = ImmutableOpenMap.builder(currentTemplates);
//        builder.put(TEMPLATE_NAME, defaultTemplate);
//        return builder.build();
//    }



}
