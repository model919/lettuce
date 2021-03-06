package com.lambdaworks.redis.cluster;

import static com.lambdaworks.redis.cluster.ClusterTestUtil.getNodeId;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.lambdaworks.redis.internal.LettuceLists;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import rx.Observable;

import com.lambdaworks.redis.FastShutdown;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.api.async.RedisAsyncCommands;
import com.lambdaworks.redis.cluster.api.rx.RedisClusterReactiveCommands;
import com.lambdaworks.redis.cluster.models.slots.ClusterSlotRange;
import com.lambdaworks.redis.cluster.models.slots.ClusterSlotsParser;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SuppressWarnings("unchecked")
public class ClusterReactiveCommandTest extends AbstractClusterTest {

    protected static RedisClient client;

    protected RedisClusterReactiveCommands<String, String> reactive;
    protected RedisAsyncCommands<String, String> async;

    @BeforeClass
    public static void setupClient() throws Exception {
        setupClusterClient();
        client = RedisClient.create(RedisURI.Builder.redis(host, port1).build());
        clusterClient = RedisClusterClient.create(LettuceLists.unmodifiableList(RedisURI.Builder.redis(host, port1).build()));

    }

    @AfterClass
    public static void shutdownClient() {
        shutdownClusterClient();
        FastShutdown.shutdown(client);
        FastShutdown.shutdown(clusterClient);
    }

    @Before
    public void before() throws Exception {

        clusterRule.getClusterClient().reloadPartitions();

        async = client.connectAsync(RedisURI.Builder.redis(host, port1).build());
        reactive = async.getStatefulConnection().reactive();
    }

    @After
    public void after() throws Exception {
        async.close();
    }

    @Test
    public void testClusterBumpEpoch() throws Exception {

        String result = first(reactive.clusterBumpepoch());

        assertThat(result).matches("(BUMPED|STILL).*");
    }

    @Test
    public void testClusterInfo() throws Exception {

        String status = first(reactive.clusterInfo());

        assertThat(status).contains("cluster_known_nodes:");
        assertThat(status).contains("cluster_slots_fail:0");
        assertThat(status).contains("cluster_state:");
    }

    @Test
    public void testClusterNodes() throws Exception {

        String string = first(reactive.clusterNodes());

        assertThat(string).contains("connected");
        assertThat(string).contains("master");
        assertThat(string).contains("myself");
    }

    @Test
    public void testClusterNodesSync() throws Exception {

        String string = first(reactive.clusterNodes());

        assertThat(string).contains("connected");
        assertThat(string).contains("master");
        assertThat(string).contains("myself");
    }

    @Test
    public void testClusterSlaves() throws Exception {

        Long replication = first(reactive.waitForReplication(1, 5));
        assertThat(replication).isNotNull();
    }

    @Test
    public void testAsking() throws Exception {
        assertThat(first(reactive.asking())).isEqualTo("OK");
    }

    @Test
    public void testClusterSlots() throws Exception {

        List<Object> reply = reactive.clusterSlots().toList().toBlocking().first();
        assertThat(reply.size()).isGreaterThan(1);

        List<ClusterSlotRange> parse = ClusterSlotsParser.parse(reply);
        assertThat(parse).hasSize(2);

        ClusterSlotRange clusterSlotRange = parse.get(0);
        assertThat(clusterSlotRange.getFrom()).isEqualTo(0);
        assertThat(clusterSlotRange.getTo()).isEqualTo(11999);

        assertThat(clusterSlotRange.getMaster()).isNotNull();
        assertThat(clusterSlotRange.getSlaves()).isNotNull();
        assertThat(clusterSlotRange.toString()).contains(ClusterSlotRange.class.getSimpleName());
    }

    @Test
    public void clusterSlaves() throws Exception {

        String nodeId = getNodeId(async.getStatefulConnection().sync());
        List<String> result = reactive.clusterSlaves(nodeId).toList().toBlocking().first();

        assertThat(result.size()).isGreaterThan(0);
    }

    private <T> T first(Observable<T> observable) {
        return observable.toBlocking().first();
    }

}
