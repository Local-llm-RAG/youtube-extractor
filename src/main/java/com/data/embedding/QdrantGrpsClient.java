package com.data.embedding;

import com.data.config.QdrantGrpcConfig;
import com.data.external.rest.pythonclient.dto.EmbedTranscriptResponse;
import com.data.jpa.dao.ChannelDao;
import com.data.jpa.dao.Video;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

@Component
@Getter
public class QdrantGrpsClient {
    private final QdrantClient client;
    private final QdrantGrpcConfig config;

    public QdrantGrpsClient(QdrantGrpcConfig config) {
        this.config = config;
        this.client = new QdrantClient(QdrantGrpcClient
                .newBuilder(config.getHost(), config.getPort(), false)
                .withApiKey(config.getApiKey())
                .build()
        );
    }

    public void insertPoints(ChannelDao channel,
                             Video video,
                             EmbedTranscriptResponse embeddingResponse,
                             String collectionName) {


        int numberOfChunks = embeddingResponse.embeddings().size();
        List<Points.PointStruct> qdrantChunks = IntStream.range(0, numberOfChunks)
                .mapToObj(chunkNumber -> {
                    String transcriptionId = buildPointId(channel.getName(), video.getTitle(), chunkNumber);
                    String uuid = UUID.nameUUIDFromBytes(transcriptionId.getBytes(StandardCharsets.UTF_8)).toString();

                    List<Float> vector = embeddingResponse.embeddings().get(chunkNumber);
                    Points.Vectors vectors = Points.Vectors.newBuilder()
                            .setVector(Points.Vector.newBuilder().addAllData(vector).build())
                            .build();


                    Points.PointStruct.Builder b = Points.PointStruct.newBuilder()
                            .setId(Common.PointId.newBuilder().setUuid(uuid).build())
                            .setVectors(vectors);


// Payload fields (THIS is the correct way)
                    b.putPayload("chunkIndex", v(chunkNumber));
                    b.putPayload("transcriptId", v(transcriptionId));
                    b.putPayload("channelName", v(channel.getName()));
                    b.putPayload("videoTitle", v(video.getTitle()));
                    b.putPayload("model", v(embeddingResponse.model()));
                    b.putPayload("dim", v(embeddingResponse.dim()));
                    b.putPayload("chunk", v(embeddingResponse.chunks().get(chunkNumber)));
                    b.putPayload("span", spanValue(embeddingResponse.spans().get(chunkNumber)));


                    return b.build();
                })
                .toList();


        Points.UpsertPoints upsert = Points.UpsertPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllPoints(qdrantChunks)
                .build();


        try {
            client.upsertAsync(upsert).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }


    private static List<Float> toFloatVector(List<Double> doubles) {
        return doubles.stream()
                .map(d -> d == null ? 0f : d.floatValue())
                .toList();
    }


    private static JsonWithInt.Value v(String s) {
        return JsonWithInt.Value.newBuilder().setStringValue(s == null ? "" : s).build();
    }


    private static JsonWithInt.Value v(int n) {
        return JsonWithInt.Value.newBuilder().setIntegerValue(n).build();
    }


    private static JsonWithInt.Value spanValue(List<Integer> span) {
        JsonWithInt.ListValue lv = JsonWithInt.ListValue.newBuilder()
                .addValues(JsonWithInt.Value.newBuilder().setIntegerValue(span.get(0)).build())
                .addValues(JsonWithInt.Value.newBuilder().setIntegerValue(span.get(1)).build())
                .build();
        return JsonWithInt.Value.newBuilder().setListValue(lv).build();
    }

    private static String buildPointId(String channelName, String videoTitle, Integer chunkId) {
        return  channelName + "-" + videoTitle + "-" + chunkId;
    }

    public Boolean healthCheck() throws ExecutionException, InterruptedException {
        return client.healthCheckAsync().get().isInitialized();
    }

}
