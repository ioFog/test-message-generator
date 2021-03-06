/*
 * *******************************************************************************
 *   Copyright (c) 2018 Edgeworx, Inc.
 *
 *   This program and the accompanying materials are made available under the
 *   terms of the Eclipse Public License v. 2.0 which is available at
 *   http://www.eclipse.org/legal/epl-2.0
 *
 *   SPDX-License-Identifier: EPL-2.0
 * *******************************************************************************
 */

package org.eclipse.iofog.ws.manager.listener;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.eclipse.iofog.tmg.manager.TMGMessageManager;
import org.eclipse.iofog.utils.ByteUtils;
import org.eclipse.iofog.utils.IOMessageUtils;
import org.eclipse.iofog.utils.elements.IOMessage;
import org.eclipse.iofog.ws.manager.WebSocketManager;

import java.util.Arrays;

/**
 * Test Message Generator WebSocket Listener
 * Implementation of {@link WebSocketManagerListener}.
 * According to specification handles next transmissions' codes:
 * In case of receiving MESSAGE from ioContainer, Test Message Generator saves IOMessage with generate ID and timestamp
 * to receivedmessages.xml and responds to ioContainer with MESSAGE_RECEIPT response containing only generated ID and timestamp.
 **/
public class TMGWSManagerListener implements WebSocketManagerListener {

    @Override
    public void handle(WebSocketManager wsManager, BinaryWebSocketFrame frame, ChannelHandlerContext ctx) {
        ByteBuf content = frame.content();
        if(content.isReadable()) {
            Byte opcode = content.readByte();
            if (opcode.equals(WebSocketManager.OPCODE_MSG)) {
                System.out.println("GOT MSG via SOCKET");
                byte[] byteArray = new byte[content.readableBytes()];
                int readerIndex = content.readerIndex();
                content.getBytes(readerIndex, byteArray);
                int totalMsgLength = ByteUtils.bytesToInteger(Arrays.copyOfRange(byteArray, 0, 4));
                IOMessage message = new IOMessage(Arrays.copyOfRange(byteArray, 4, totalMsgLength + 4));
                message.setId(IOMessageUtils.generateID());
                message.setTimestamp(System.currentTimeMillis());
//                System.out.println("Message: \n" + message.toString());
                TMGMessageManager.saveMessage(message);
                wsManager.sendReceipt(ctx, message.getId(), message.getTimestamp());
            }
        }
    }


}
