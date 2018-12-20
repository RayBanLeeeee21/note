package com.raybanleeeee.study.distributedsystem.communicate.reqreply;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;


public class ReqReplyProtocol {

    public static final int DEFAULT_MAX_RETRY = 5;
    public static final int DEFAULT_MAX_BUF_SIZE = 1024;
    public static final ReqReplyCodec DEFAULT_REQ_REPLY_CODEC = new DefaultReqReplyCodec();

    public static ReqReplyClient newReqReplyClient(DatagramSocket localSocket, InetSocketAddress serverAddr, ReqReplyCodec codec,
                                                   int maxRetry, int maxBufSize) {
        return new ReqReplyClient(localSocket, serverAddr, codec, maxRetry, maxBufSize);
    }

    public static ReqReplyClient newReqReplyClient(DatagramSocket localSocket, InetSocketAddress serverAddr) {

        return new ReqReplyClient(localSocket, serverAddr, DEFAULT_REQ_REPLY_CODEC, DEFAULT_MAX_RETRY, DEFAULT_MAX_BUF_SIZE);
    }

    public static ReqReplyServer newReqReplyServer(DatagramSocket localSocket, ReqReplyCodec codec, int maxBufSize,
                                                   Function<byte[], byte[]> messageProcessor) {
        return new ReqReplyServer(localSocket, codec, maxBufSize, messageProcessor);
    }

    public static ReqReplyServer newReqReplyServer(DatagramSocket localSocket,
                                                   Function<byte[], byte[]> messageProcessor) {

        return new ReqReplyServer(localSocket, DEFAULT_REQ_REPLY_CODEC, DEFAULT_MAX_BUF_SIZE, messageProcessor);
    }

    public static class ReqReplyClient {
        private final DatagramSocket localSocket;
        private final InetSocketAddress serverAddr;
        private final ReqReplyCodec codec;
        private final int maxRetry;
        private final int maxBufSize;
        private final AtomicInteger requestId = new AtomicInteger(0);

        private ReqReplyClient(DatagramSocket localSocket, InetSocketAddress serverAddr, ReqReplyCodec codec, int maxRetry, int maxBufSize
        ) {
            this.localSocket = localSocket;
            this.serverAddr = serverAddr;
            this.codec = codec;
            this.maxRetry = maxRetry;
            this.maxBufSize = maxBufSize;
        }

        public byte[] request(byte[] message) throws IOException {
            DatagramPacket requestUdp = codec.encodeRequestToUdp(
                    requestId.get(),
                    message,
                    this.serverAddr
            );
            DatagramPacket responseUdp = new DatagramPacket(
                    new byte[this.maxBufSize],
                    this.maxBufSize
            );

            ReqReplyMessage response;
            for (int i = 0; i < this.maxRetry; i++) {
                this.localSocket.send(requestUdp);

                try {
                    this.localSocket.receive(responseUdp);
                }catch(SocketTimeoutException e) {
                    continue;
                }

                if ((response = codec.decodeResponseUdp(responseUdp, requestId.get())) != null) {
                    requestId.getAndIncrement();
                    return response.getMessage();
                }

            }
            throw new SocketTimeoutException();
        }

    }

    public static class ReqReplyServer {

        private final DatagramSocket localSocket;
        private final ReqReplyCodec codec;
        private final int maxBufSize;

        private final Function<byte[], byte[]> messageProcessor;

        private ReqReplyServer(DatagramSocket localSocket, ReqReplyCodec codec, int maxBufSize,
                               Function<byte[], byte[]> messageProcessor) {
            this.localSocket = localSocket;
            this.codec = codec;
            this.maxBufSize = maxBufSize;
            this.messageProcessor = messageProcessor;
        }

        public byte[] response() throws IOException {

            DatagramPacket requestUdp = new DatagramPacket(new byte[maxBufSize], maxBufSize);
            this.localSocket.receive(requestUdp);

            ReqReplyMessage request = null;
            try {
                request = codec.decodeRequestUdp(requestUdp);
            } catch (Exception e) {
            }
            if (request == null)
                return null;

            DatagramPacket responseUdp = codec.encodeResponseToUdp(
                    request.getId() + 1,
                    messageProcessor.apply(request.getMessage()),
                    requestUdp.getSocketAddress()
            );
            this.localSocket.send(responseUdp);

            return request.getMessage();
        }

    }
}
