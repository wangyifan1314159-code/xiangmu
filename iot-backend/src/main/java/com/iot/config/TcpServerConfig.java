package com.iot.config;

import com.iot.service.TcpMessageHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Netty TCP 服务器：设备 JSON 行协议接入通道。
 *
 * <p>协议为 UTF-8 + '\n' 结尾的单行 JSON（LineBasedFrameDecoder），
 * 消息处理见 {@link TcpMessageHandler}。启用开关：app.tcp.enabled（默认 true）。
 */
@Configuration
@ConditionalOnProperty(name = "app.tcp.enabled", havingValue = "true")
@Slf4j
public class TcpServerConfig {

    @Value("${app.tcp.port:1884}")
    private int port;

    @Value("${app.tcp.max-frame-length:65536}")
    private int maxFrameLength;

    @Value("${app.tcp.auth-timeout-seconds:30}")
    private int authTimeoutSeconds;

    /** 已认证设备的读空闲超时（秒）。
     *  设备端应每 60 秒发一次 heartbeat，该值应 > 60。
     *  设置为 0 时禁用（不推荐生产使用）。 */
    @Value("${app.tcp.read-idle-seconds:90}")
    private int readIdleSeconds;

    private final TcpMessageHandler messageHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverFuture;

    public TcpServerConfig(TcpMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    // 不能使用 @PostConstruct 的 void 方式；改为在构造函数中启动，
    // 或在 @PostConstruct 中 try/catch 不阻塞 Spring 启动。
    @jakarta.annotation.PostConstruct
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new LineBasedFrameDecoder(maxFrameLength));
                            pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                            pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
                            // 未认证连接 N 秒无数据即断开；认证后由 handler 决定是否继续计时
                            pipeline.addLast(new IdleStateHandler(authTimeoutSeconds, 0, 0));
                            pipeline.addLast(messageHandler);
                        }
                    });

            serverFuture = bootstrap.bind(port).sync();
            log.info("TCP device server started on port {} (JSON line protocol)", port);
        } catch (Exception e) {
            log.error("TCP device server failed to bind port {}: {}", port, e.getMessage());
            shutdownGroups();
        }
    }

    @PreDestroy
    public void stop() {
        if (serverFuture != null) {
            serverFuture.channel().close().syncUninterruptibly();
        }
        shutdownGroups();
        log.info("TCP device server stopped");
    }

    private void shutdownGroups() {
        if (bossGroup != null) bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        if (workerGroup != null) workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
    }
}
