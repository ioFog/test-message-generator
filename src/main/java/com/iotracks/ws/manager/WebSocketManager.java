package com.iotracks.ws.manager;

import com.iotracks.utils.ByteUtils;
import com.iotracks.ws.manager.listener.WebSocketManagerListener;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;


/**
 * Manager for all WebSocket communications between Container and ioFog.
 */
public class WebSocketManager {

    public static final Byte OPCODE_PING = 0x9;
    public static final Byte OPCODE_PONG = 0xA;
    public static final Byte OPCODE_ACK = 0xB;
    public static final Byte OPCODE_CONTROL_SIGNAL = 0xC;
    public static final Byte OPCODE_MSG = 0xD;
    public static final Byte OPCODE_RECEIPT = 0xE;

    private Map<String, ChannelHandlerContext> mControlWebsocketMap;
    private Map<String, ChannelHandlerContext> mMessageWebsocketMap;
    private Map<ChannelHandlerContext, AckMarker> mMessageSendContextMap;
    private Map<ChannelHandlerContext, Integer> mControlSignalSendContextMap;
    private Set<ChannelHandlerContext> mPingSendMap;

    private WebSocketManagerListener wsListener;
    private ScheduledExecutorService mScheduler;

    public WebSocketManager(WebSocketManagerListener wsListener) {
        mControlWebsocketMap = new ConcurrentHashMap<>();
        mMessageWebsocketMap = new ConcurrentHashMap<>();
        mMessageSendContextMap = new ConcurrentHashMap<>();
        mControlSignalSendContextMap = new ConcurrentHashMap<>();
        mPingSendMap = Collections.synchronizedSet(new HashSet<>());

        mScheduler = Executors.newScheduledThreadPool(4);
        mScheduler.scheduleWithFixedDelay(new MessageWatcher(mMessageSendContextMap, this), 0, 5, TimeUnit.SECONDS);
        mScheduler.scheduleWithFixedDelay(new ControlWatcher(mControlSignalSendContextMap, this), 0, 10, TimeUnit.SECONDS);
        mScheduler.scheduleWithFixedDelay(new PingWatcher(mPingSendMap, mControlWebsocketMap, this), 0, 5, TimeUnit.SECONDS);
        mScheduler.scheduleWithFixedDelay(new PingWatcher(mPingSendMap, mMessageWebsocketMap, this), 0, 10, TimeUnit.SECONDS);
        this.wsListener = wsListener;
    }

    public void sendMessage(String publisherId, byte[] pData) {
        ChannelHandlerContext ctx = mMessageWebsocketMap.get(publisherId);
        if (ctx != null) {
            sendMessage(ctx, pData);
        } else {
            throw new IllegalArgumentException("Context not found.");
        }
    }

    private void sendMessage(ChannelHandlerContext pCtx, byte[] pData) {
        byte[] header = new byte[5];
        byte[] bLenArr = ByteUtils.integerToBytes(pData.length);
        header[0] = OPCODE_MSG;
        header[1] = bLenArr[0];
        header[2] = bLenArr[1];
        header[3] = bLenArr[2];
        header[4] = bLenArr[3];

        byte[] msg = new byte[header.length + pData.length];
        int i = 0;
        for (byte b : header) {
            msg[i] = b;
            i++;
        }
        for (byte b : pData) {
            msg[i] = b;
            i++;
        }
        sendBinaryFrame(pCtx, msg);

        AckMarker marker = mMessageSendContextMap.get(pCtx);
        if (marker == null) {
            marker = new AckMarker(10, pData);
            mMessageSendContextMap.put(pCtx, marker);
        }
    }

    public void sendControl(String publisherId) {
        ChannelHandlerContext ctx = mControlWebsocketMap.get(publisherId);
        if (ctx != null) {
            sendControl(ctx);
        } else {
            throw new IllegalArgumentException("Context not found.");
        }
    }

    private void sendControl(ChannelHandlerContext pCtx) {
        //sendBinaryFrame(pCtx, new byte[]{OPCODE_CONTROL_SIGNAL});
        ByteBuf buffer1 = pCtx.alloc().buffer();
        buffer1.writeByte(OPCODE_CONTROL_SIGNAL);
//        buffer1.writeByte(Byte.SIZE);
        pCtx.channel().writeAndFlush(new BinaryWebSocketFrame(buffer1));
        Integer cnt = mControlSignalSendContextMap.get(pCtx);
        if (cnt == null) {
            mControlSignalSendContextMap.put(pCtx, 10);
        }
    }

    public void sendReceipt(ChannelHandlerContext pCtx, String pMessageId, Long pMessageTimestamp) {
        ByteBuf buffer1 = pCtx.alloc().buffer();
        buffer1.writeByte(OPCODE_RECEIPT.intValue());
        //send Length
        int msgIdLength = pMessageId.length();
        buffer1.writeByte(msgIdLength);
        buffer1.writeByte(Long.BYTES);

        //Send opcode, id and timestamp
        buffer1.writeBytes(pMessageId.getBytes());
        buffer1.writeBytes(ByteUtils.longToBytes(pMessageTimestamp));
        pCtx.channel().writeAndFlush(new BinaryWebSocketFrame(buffer1));
    }

    private void sendPing(ChannelHandlerContext pCtx) {
        ByteBuf buffer1 = pCtx.alloc().buffer();
        buffer1.writeByte(OPCODE_PING.intValue());
        pCtx.channel().writeAndFlush(new PingWebSocketFrame(buffer1));
    }

    private void sendBinaryFrame(ChannelHandlerContext pCtx, byte[] pData) {
        if (!isCtxActual(pCtx)) {
            throw new IllegalArgumentException("Context not found.");
        }
        ByteBuf buffer1 = pCtx.alloc().buffer();
        buffer1.writeBytes(pData);
        pCtx.channel().writeAndFlush(new BinaryWebSocketFrame(buffer1));
    }

    private void sendFrame(ChannelHandlerContext pCtx, WebSocketFrame pFrame) {
        pCtx.channel().writeAndFlush(pFrame);
    }

    private void closeSocket(ChannelHandlerContext pCtx) {
        System.out.println("closing socket");
        sendFrame(pCtx, new CloseWebSocketFrame());
        pCtx.channel().close();
        invalidateCtx(pCtx);
    }

    public void eatFrame(ChannelHandlerContext pCtx, WebSocketFrame pFrame) {
        boolean handled = handleClose(pCtx, pFrame);
        if (!handled) {
            handled = handlePing(pCtx, pFrame);
        }
        if (!handled) {
            handled = handleAck(pCtx, pFrame);
        }
        if (!handled) {
            handled = handlePong(pCtx, pFrame);
        }
        if (!handled) {
            handleData(pCtx, pFrame);
        }
    }

    private void handleData(ChannelHandlerContext pCtx, WebSocketFrame pFrame) {
        System.out.println("GOT some bin data via SOCKET");
        if (pFrame instanceof BinaryWebSocketFrame) {
            wsListener.handle(this, (BinaryWebSocketFrame) pFrame, pCtx);
        }
    }


    private boolean handleAck(ChannelHandlerContext pCtx, WebSocketFrame pFrame) {
        if (pFrame instanceof BinaryWebSocketFrame) {
            ByteBuf buffer2 = pFrame.content();
            if (buffer2.readableBytes() == 1) {
                Byte opcode = buffer2.readByte();
                if (opcode.equals(OPCODE_ACK)) {
                    System.out.println("GOT OPCODE_ACK via SOCKET");
                    invalidateAck(pCtx);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleClose(ChannelHandlerContext pCtx, WebSocketFrame pFrame) {
        if (pFrame instanceof CloseWebSocketFrame) {
            System.out.println("close received");
            pCtx.channel().close();
            invalidateCtx(pCtx);
            return true;
        }
        return false;
    }

    private boolean handlePong(ChannelHandlerContext pCtx, WebSocketFrame pFrame) {
        if (pFrame instanceof PongWebSocketFrame) {
            ByteBuf buffer = pFrame.content();
            if (buffer.readableBytes() == 1) {
                Byte opcode = buffer.readByte();
                if (opcode.equals(OPCODE_PONG)) {
                    if (isCtxActual(pCtx)) {
                        mPingSendMap.remove(pCtx);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean handlePing(ChannelHandlerContext pCtx, WebSocketFrame pFrame) {
        if (pFrame instanceof PingWebSocketFrame) {
            ByteBuf buffer = pFrame.content();
            if (buffer.readableBytes() == 1) {
                Byte opcode = buffer.readByte();
                if (opcode.equals(OPCODE_PING)) {
                    if (isCtxActual(pCtx)) {
                        ByteBuf buffer1 = pCtx.alloc().buffer();
                        buffer1.writeByte(OPCODE_PONG.intValue());
                        sendFrame(pCtx, new PongWebSocketFrame(buffer1));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void invalidateAck(ChannelHandlerContext pCtx) {
        removeCtxFromMap(pCtx, mControlWebsocketMap);
        mMessageSendContextMap.remove(pCtx);
    }

    private void invalidateCtx(ChannelHandlerContext pCtx) {
        removeCtxFromMap(pCtx, mControlWebsocketMap);
        removeCtxFromMap(pCtx, mMessageWebsocketMap);
    }

    private static void removeCtxFromMap(ChannelHandlerContext pCtx, Map<String, ChannelHandlerContext> pCtxMap) {
        for (String id : pCtxMap.keySet()) {
            ChannelHandlerContext curCtx = pCtxMap.get(id);
            if (curCtx.equals(pCtx)) {
                pCtxMap.remove(id);
            }
        }
    }

    private boolean isCtxActual(ChannelHandlerContext pCtx) {
        return isControlCtx(pCtx) || isMessageCtx(pCtx);
    }

    private boolean isControlCtx(ChannelHandlerContext pCtx) {
        return isCtxInMap(pCtx, mControlWebsocketMap);
    }

    private boolean isMessageCtx(ChannelHandlerContext pCtx) {
        return isCtxInMap(pCtx, mMessageWebsocketMap);
    }

    private static boolean isCtxInMap(ChannelHandlerContext pCtx, Map<String, ChannelHandlerContext> pCtxMap) {
        return pCtxMap.values().contains(pCtx);
    }

    public void initMessageSocket(ChannelHandlerContext pCtx, String pContainerId, boolean pSsl, String pUrl,
                                  FullHttpRequest pReq) {
        initSocket(pCtx, pContainerId, pSsl, pUrl, pReq, mMessageWebsocketMap);
    }

    public void initControlSocket(ChannelHandlerContext pCtx, String pContainerId, boolean pSsl, String pUrl,
                                  FullHttpRequest pReq) {
        initSocket(pCtx, pContainerId, pSsl, pUrl, pReq, mControlWebsocketMap);
    }

    private static void initSocket(ChannelHandlerContext pCtx, String pContainerId, boolean pSsl, String pUrl,
                                   FullHttpRequest pReq, Map<String, ChannelHandlerContext> pSocketMap) {
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(buildWebSocketLocation(pSsl, pUrl, pReq), null, true);
        WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(pReq);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(pCtx.channel());
        } else {
            handshaker.handshake(pCtx.channel(), pReq);
        }
        pSocketMap.put(pContainerId, pCtx);
    }


    private static String buildWebSocketLocation(boolean pSsl, String wsPath, FullHttpRequest pReq) {
        String location = pReq.headers().get(HOST) + wsPath;
        if (pSsl) {
            return "wss://" + location;
        } else {
            return "ws://" + location;
        }
    }

    private static class AckMarker {
        private int mSendCnt;
        private byte[] mData;

        AckMarker(int pSendCnt, byte[] pData) {
            mSendCnt = pSendCnt;
            mData = pData;
        }

        int getSendCnt() {
            return mSendCnt;
        }

        byte[] getData() {
            return mData;
        }

        void trying() {
            mSendCnt--;
        }
    }

    private class MessageWatcher implements Runnable {

        private Map<ChannelHandlerContext, AckMarker> mMessageSendContextMap;
        private WebSocketManager mSocketManager;

        MessageWatcher(Map<ChannelHandlerContext, AckMarker> pMessageSendContextMap, WebSocketManager pSocketManager) {
            mMessageSendContextMap = pMessageSendContextMap;
            mSocketManager = pSocketManager;
        }

        @Override
        public void run() {
            for (ChannelHandlerContext ctx : mMessageSendContextMap.keySet()) {
                AckMarker marker = mMessageSendContextMap.get(ctx);
                if (marker.getSendCnt() > 0) {
                    marker.trying();
                    mSocketManager.sendMessage(ctx, marker.getData());
                } else {
                    mMessageSendContextMap.remove(ctx);
                    System.out.println("Closing socket in message watcher");
                    mSocketManager.closeSocket(ctx);
                }
            }
        }
    }

    private class ControlWatcher implements Runnable {

        private Map<ChannelHandlerContext, Integer> mControlSignalSendContextMap;
        private WebSocketManager mSocketManager;

        ControlWatcher(Map<ChannelHandlerContext, Integer> pControlSignalSendContextMap,
                       WebSocketManager pSocketManager) {
            mControlSignalSendContextMap = pControlSignalSendContextMap;
            mSocketManager = pSocketManager;
        }

        @Override
        public void run() {
            for (ChannelHandlerContext ctx : mControlSignalSendContextMap.keySet()) {
                int cnt = mControlSignalSendContextMap.get(ctx);
                if (cnt > 0) {
                    cnt--;
                    mControlSignalSendContextMap.put(ctx, cnt);
                    mSocketManager.sendControl(ctx);
                } else {
                    mControlSignalSendContextMap.remove(ctx);
                    System.out.println("Closing socket in control watcher");
                    mSocketManager.closeSocket(ctx);
                }
            }
        }
    }

    private class PingWatcher implements Runnable {

        private WebSocketManager mSocketManager;
        private Set<ChannelHandlerContext> mPingSendMap;
        private Map<String, ChannelHandlerContext> mWebsocketMap;

        PingWatcher(Set<ChannelHandlerContext> pPingSendMap, Map<String, ChannelHandlerContext> pWebsocketMap,
                    WebSocketManager pSocketManager) {
            mSocketManager = pSocketManager;
            mPingSendMap = pPingSendMap;
            mWebsocketMap = pWebsocketMap;
        }

        @Override
        public void run() {
            for (ChannelHandlerContext ctx : mPingSendMap) {
                System.out.println("Closing socket in ping watcher");
                mSocketManager.closeSocket(ctx);
                mPingSendMap.remove(ctx);
            }

            for (String id : mWebsocketMap.keySet()) {
                ChannelHandlerContext ctx = mWebsocketMap.get(id);
                mPingSendMap.add(ctx);
                mSocketManager.sendPing(ctx);
            }
        }
    }
}
