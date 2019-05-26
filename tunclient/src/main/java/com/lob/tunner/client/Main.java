package com.lob.tunner.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.lob.tunner.common.Config;
import com.lob.tunner.logger.AutoLog;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

// import java.util.logging.Level;
// import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create a listening port to accept client connections, read-in data and multiplexing the data on
 * one of the tunnels
 */
public class Main {
    public final static EventLoopGroup REMOTEWORKER =new NioEventLoopGroup(1);
    public final static EventLoopGroup LOCALWORKER = new NioEventLoopGroup(1);

    private final static TunnelManager _tunnelManager = TunnelManager.getInstance();
    private final static EventLoopGroup BOSS = new NioEventLoopGroup(1);

    public static void main(String[] args){
        AutoLog.INFO.log("Disable logging ...");
        Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.OFF);

        Config.initialize(args, true);

        try {
            _tunnelManager.start();

            /**
             * Handle client APP connection.
             * When client APP connecting, we create a Connection object to wrap the socket channel, which will
             * 1. read data from client, forward the data to one of the tunnel (create if not existing)
             * 2. read data from assigned tunnel, forward the data to client
             */
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(BOSS, LOCALWORKER)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new ClientConnectionHandler(channel));
                        }
                    });

            int port = Config.getListenPort();
            ChannelFuture future = bootstrap.bind(port ).sync();

            AutoLog.INFO.log("Starting at port " + port);

            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            BOSS.shutdownGracefully();
            LOCALWORKER.shutdownGracefully();
            REMOTEWORKER.shutdownGracefully();
        }
    }
}
