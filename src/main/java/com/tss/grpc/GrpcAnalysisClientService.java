package com.tss.grpc;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.tss.grpc.analysis.AnalysisResponse;
import com.tss.grpc.analysis.AnalysisServiceGrpc;
import com.tss.grpc.analysis.AnalyzeRequest;

@Service
public class GrpcAnalysisClientService {

    private final AnalysisServiceGrpc.AnalysisServiceBlockingStub stub;

    public GrpcAnalysisClientService(AnalysisServiceGrpc.AnalysisServiceBlockingStub stub) {
        this.stub = stub;
    }

    public AnalysisResponse analyzeUser(String login) {
        return stub.withDeadlineAfter(30, TimeUnit.SECONDS)
                .analyzeUser(AnalyzeRequest.newBuilder()
                        .setLogin(login)
                        .build());
    }

    public List<?> getNewNotifications(String login) {
        return analyzeUser(login).getNewNotificationsList();
    }
}