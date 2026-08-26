package com.iot.service;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.tcp.enabled", havingValue = "true")
public class TcpListenerManager implements AutoCloseable {

    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final ServerBootstrap bootstrap;
    private final Map<Integer, Channel> listeners = new ConcurrentHashMap<>();

    public TcpListenerManager(TcpMessageHandler messageHandler,
                              @Value("${app.tcp.max-frame-length:65536}") int maxFrameLength,
                              @Value("${app.tcp.auth-timeout-seconds:30}") int authTimeoutSeconds) {
        bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast(new LineBasedFrameDecoder(maxFrameLength));
                        pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                        pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
                        pipeline.addLast(new IdleStateHandler(authTimeoutSeconds, 0, 0));
                        pipeline.addLast(messageHandler);
                    }
                });
    }

    public synchronized Map<String, Object> start(int port) {
        validatePort(port);
        if (listeners.containsKey(port)) {
            throw new IllegalStateException("TCP 监听端口已启动: " + port);
        }
        Channel channel = bootstrap.bind(port).syncUninterruptibly().channel();
        listeners.put(port, channel);
        channel.closeFuture().addListener(ignored -> listeners.remove(port, channel));
        return listener(port, channel);
    }

    public List<Map<String, Object>> listListeners() {
        return listeners.entrySet().stream()
                .filter(entry -> entry.getValue().isActive())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> listener(entry.getKey(), entry.getValue()))
                .toList();
    }

    public boolean stop(int port) {
        validatePort(port);
        Channel channel = listeners.remove(port);
        if (channel == null) {
            return false;
        }
        channel.close().syncUninterruptibly();
        return true;
    }

    @Override
    @PreDestroy
    public void close() {
        listeners.values().forEach(channel -> channel.close().syncUninterruptibly());
        listeners.clear();
        bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
    }

    private static void validatePort(int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("TCP 端口必须在 1 到 65535 之间");
        }
    }

    private static Map<String, Object> listener(int port, Channel channel) {
        return Map.of("port", port, "status", channel.isActive() ? "LISTENING" : "STOPPED");
    }
}
