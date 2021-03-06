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

package org.eclipse.iofog.tmg.manager;

import org.eclipse.iofog.utils.IOMessageConverter;
import org.eclipse.iofog.utils.TMGFileUtils;
import org.eclipse.iofog.utils.elements.IOMessage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Test Message Generator Manager of IOMessages for mimicking communication with ioFog.
 *
 * @author Eclipse ioFog { Iryna Laryionava, Pavel Kazlou, Sasha Yakovtseva }
 * @since 3/29/16.
 */
public class TMGMessageManager {

    private static final Logger log = Logger.getLogger(TMGMessageManager.class.getName());

    private static final String MESSAGES_FILE_SOURCE = "messages.xml";
    private static final String RECEIVED_MESSAGES_FILE_SOURCE = "receivedmessages.xml";
    public static final String IO_MESSAGE_TAG_NAME = "iomessage";
    private static final String CONTAINER_CONFIG_FILE_SOURCE = "containerconfig.json";

    private static Document messagesFile;
    private static Document receivedMessagesFile;
    private static JsonObject containerConfig;

    /**
     * Method returns all IOMessages from messages.xml.
     *
     * @return List<IOMessage>
     */
    public static List<IOMessage> getAllMessages() {
        NodeList xmlMessagesList = getMessagesFile().getElementsByTagName(IO_MESSAGE_TAG_NAME);
        List<IOMessage> messages = new ArrayList<>(xmlMessagesList.getLength());
        for (int i = 0; i < xmlMessagesList.getLength(); i++) {
            Node xmlMessage = xmlMessagesList.item(i);
            messages.add(IOMessageConverter.getMessageFromNode(xmlMessage));
        }
        return messages;
    }

    /**
     * Method returns random IOMessage from messages.xml
     *
     * @return IOMessage
     */
    public static IOMessage getRandomMessage() {
        NodeList xmlMessagesList = getMessagesFile().getElementsByTagName(IO_MESSAGE_TAG_NAME);
        int randomIndex = new Random().nextInt(xmlMessagesList.getLength());
        return IOMessageConverter.getMessageFromNode(xmlMessagesList.item(randomIndex));
    }

    /**
     * Method saves IOMessages to receivedmessages.xml
     *
     * @param message - IOMessage to be saved
     */
    public static void saveMessage(IOMessage message) {
        Element rootElement = getReceivedMessagesFile().getDocumentElement();
        rootElement.appendChild(IOMessageConverter.getElementFromMessage(message, getReceivedMessagesFile()));
        TMGFileUtils.saveFile(getReceivedMessagesFile(), RECEIVED_MESSAGES_FILE_SOURCE);
    }

    /**
     * Method returns Json representation of new configurations for ioContainer from containerconfig.json
     *
     * @return JsonObject
     */
    public static JsonObject getContainerConfig() {
        if (containerConfig == null) {
            FileReader fileReader = TMGFileUtils.readFile(CONTAINER_CONFIG_FILE_SOURCE);
            if (fileReader != null) {
                JsonReader reader = Json.createReader(fileReader);
                JsonObject config = reader.readObject();
                containerConfig = Json.createObjectBuilder()
                                      .add("status", "okay")
                                      .add("config", config.toString()).build();
                reader.close();
                try {
                    fileReader.close();
                } catch (Exception e) {
                    log.info("Error closing file reader stream for configuration JSON file.");
                }
            }
        }
        return containerConfig;
    }

    private static Document getMessagesFile() {
        if (messagesFile == null) {
            messagesFile = TMGFileUtils.getXMLDocument(MESSAGES_FILE_SOURCE);
        }
        return messagesFile;
    }

    private static Document getReceivedMessagesFile() {
        if (receivedMessagesFile == null) {
            receivedMessagesFile = TMGFileUtils.getXMLDocument(RECEIVED_MESSAGES_FILE_SOURCE);
        }
        return receivedMessagesFile;
    }

}
