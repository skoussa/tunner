package com.lob.tunner.client;

import com.lob.tunner.BlockUtils;
import com.lob.tunner.BufferUtils;
import com.lob.tunner.common.Block;
import com.lob.tunner.logger.AutoLog;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.ReferenceCountUtil;

/**
 * ClientConnectionHandler handles a client connection by
 * 1. associate with a tunnel, if no tunnel, create one
 * 2. read incoming data from tunnel
 * 3. forward incoming data to tunnel (which will encode and wire to remote side)
 * 4. read data from tunnel
 * 5. write data to channel
 */
public class ClientConnectionHandler extends ChannelInboundHandlerAdapter {
    private EventLoopGroup remoteLoopGroup = Main.REMOTEWORKER;

    /**
     * Channel to Client APP
     */
    private final Connection _clientConnection;

    /**
     * Constructor
     * @param channel
     */
    ClientConnectionHandler(SocketChannel channel) {
        this._clientConnection = new Connection(channel);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        TunnelManager.getInstance().accept(this._clientConnection);

        super.channelRegistered(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        boolean canWrite = ctx.channel().isWritable();
        AutoLog.WARN.log(ctx.channel() + " 可写性：" + canWrite);

        _clientConnection.channel().config().setAutoRead(canWrite);
        super.channelWritabilityChanged(ctx);
    }


    /**
     * Read something on channel, let's write to server ...
     * @param localCtx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext localCtx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf)msg;

        //
        // Create a block from the buffer and send it to tunnel
        int bytes = buf.readableBytes();

        if(bytes >= 0xFFFF) {
            throw new RuntimeException("Read too large packet - " + bytes);
        }

        Block block = new Block(
                _clientConnection.getID(),
                BlockUtils.sequence(_clientConnection.nextRequest()),
                (short)bytes, BufferUtils.toNioBuffer(buf)
        );

        _clientConnection.tunnel().write(block);

        ReferenceCountUtil.release(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        AutoLog.ERROR.exception(cause).log("Caught exception");

        TunnelManager.getInstance().close(_clientConnection);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }
}
