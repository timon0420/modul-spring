package com.tss.grpc;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tss.grpc.analysis.AnalysisServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Configuration
public class GrpcAnalysisClientConfig {

    private ManagedChannel channel;

    @Bean
    public AnalysisServiceGrpc.AnalysisServiceBlockingStub analysisServiceBlockingStub(
            @Value("${analysis.grpc.host:localhost}") String host,
            @Value("${analysis.grpc.port:50051}") int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        return AnalysisServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }
}