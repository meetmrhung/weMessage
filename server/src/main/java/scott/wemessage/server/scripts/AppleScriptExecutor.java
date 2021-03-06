/*
 *  weMessage - iMessage for Android
 *  Copyright (C) 2018 Roman Scott
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package scott.wemessage.server.scripts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import scott.wemessage.commons.types.ActionType;
import scott.wemessage.commons.types.ReturnType;
import scott.wemessage.commons.utils.StringUtils;
import scott.wemessage.server.MessageServer;
import scott.wemessage.server.ServerLogger;
import scott.wemessage.server.configuration.ServerConfiguration;
import scott.wemessage.server.messages.chat.GroupChat;
import scott.wemessage.server.weMessage;

public final class AppleScriptExecutor extends Thread {

    private final String TAG = "AppleScript Runner";
    private final List<UUID> queue = Collections.synchronizedList(new ArrayList<UUID>());
    private final Object messageServerLock = new Object();
    private final Object scriptsFolderLock = new Object();
    private final Object scriptsFolderPathLock = new Object();
    private final Object tempFolderLock = new Object();

    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private MessageServer messageServer;
    private String scriptsFolderPath;
    private File scriptsFolder;
    private Path tempFolder;

    public AppleScriptExecutor(MessageServer server, ServerConfiguration serverConfiguration) {
        this.messageServer = server;

        synchronized (scriptsFolderPathLock) {
            this.scriptsFolderPath = serverConfiguration.getParentDirectoryPath() + "/scripts";
        }
        synchronized (scriptsFolderLock) {
            this.scriptsFolder = new File(getScriptsFolderPath());
        }
        try {
            synchronized (tempFolderLock) {
                this.tempFolder = Files.createTempDirectory("weServer");
            }
        }catch(IOException ex){
            ServerLogger.error(TAG, "An unknown error occurred while creating the weServer temp directory. Shutting down!", ex);
            server.shutdown(-1, false);
        }

        if (!getScriptsFolder().exists()){
            ServerLogger.error(TAG, "weServer Scripts folder could not be found. Shutting down!", new Exception());
            server.shutdown(-1, false);
        }
    }

    private MessageServer getMessageServer(){
        synchronized (messageServerLock){
            return messageServer;
        }
    }

    public Path getTempFolder(){
        synchronized (tempFolderLock){
            return tempFolder;
        }
    }

    public String getScriptsFolderPath(){
        synchronized (scriptsFolderPathLock) {
            return scriptsFolderPath;
        }
    }

    public File getScriptsFolder(){
        synchronized (scriptsFolderLock){
            return scriptsFolder;
        }
    }

    public void run(){
        isRunning.set(true);
    }

    public void stopService(){
        if (isRunning.get()){
            isRunning.set(false);
            try {
                Files.walkFileTree(getTempFolder(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                        if (e == null) {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                        throw e;
                    }
                });
            } catch (IOException e) {
                ServerLogger.error(TAG, "Failed to delete temp folder" + getTempFolder().toString(), e);
            }
        }
    }

    public Object runSendMessageScript(String handle, String fileLocation, String message){
        return runScript(ActionType.SEND_MESSAGE, new String[] { handle, fileLocation, message });
    }

    public Object runSendGroupMessageScript(GroupChat chat, String fileLocation, String message){
        ScriptChatMetadata metadata = new ScriptChatMetadata(chat);

        return runScript(ActionType.SEND_GROUP_MESSAGE, new String[] { String.valueOf(metadata.getAlgorithmicRow(getMessageServer().getMessagesDatabase())),
                metadata.getGuid(), metadata.getNameCheck(), String.valueOf(metadata.getNoNameFlag()), fileLocation, message });
    }

    public Object runAddParticipantScript(GroupChat chat, String account){
        ScriptChatMetadata metadata = new ScriptChatMetadata(chat);

        return runScript(ActionType.ADD_PARTICIPANT, new String[] { String.valueOf(metadata.getAlgorithmicRow(getMessageServer().getMessagesDatabase())),
                metadata.getNameCheck(), String.valueOf(metadata.getNoNameFlag()), account });
    }

    public Object runCreateGroupScript(String groupName, List<String> participants, String message){
        String participantsArg = StringUtils.join(participants, ",", 1);

        return runScript(ActionType.CREATE_GROUP, new String[] { groupName, participantsArg, message } );
    }

    public Object runLeaveGroupScript(GroupChat chat){
        ScriptChatMetadata metadata = new ScriptChatMetadata(chat);

        return runScript(ActionType.LEAVE_GROUP, new String[] { String.valueOf(metadata.getAlgorithmicRow(getMessageServer().getMessagesDatabase())),
                metadata.getNameCheck(), String.valueOf(metadata.getNoNameFlag()) });
    }

    public Object runRemoveParticipantScript(GroupChat chat, String account){
        ScriptChatMetadata metadata = new ScriptChatMetadata(chat);

        return runScript(ActionType.REMOVE_PARTICIPANT, new String[] { String.valueOf(metadata.getAlgorithmicRow(getMessageServer().getMessagesDatabase())),
                metadata.getNameCheck(), String.valueOf(metadata.getNoNameFlag()), account });
    }

    public Object runRenameGroupScript(GroupChat chat, String newTitle){
        ScriptChatMetadata metadata = new ScriptChatMetadata(chat);

        return runScript(ActionType.RENAME_GROUP, new String[] { String.valueOf(metadata.getAlgorithmicRow(getMessageServer().getMessagesDatabase())),
                metadata.getNameCheck(), String.valueOf(metadata.getNoNameFlag()), newTitle });
    }

    public Object runScript(final ActionType actionType, String[] args){
        File scriptFile;
        try {
            File[] scriptFiles = getScriptsFolder().listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.startsWith(actionType.getScriptName());
                }
            });
            scriptFile = scriptFiles[0];
        }catch(Exception ex){
            ServerLogger.error(TAG, "The script " + actionType.getScriptName() + ".scpt does not exist!", new NullPointerException());
            return null;
        }

        UUID uuid = UUID.randomUUID();
        AtomicBoolean shouldLoop = new AtomicBoolean(true);
        queue.add(uuid);

        while (shouldLoop.get()){
            if (queue.get(0).equals(uuid)){
                queue.remove(uuid);
                shouldLoop.set(false);
            }
        }

        Object result;
        String returnCode = null;
        Process process;

        try {
            startMessagesApp();
            switch (actionType) {
                case SEND_MESSAGE:
                    process = new ProcessBuilder("osascript", scriptFile.getAbsolutePath(), args[0], args[1], args[2]).start();
                    break;
                case SEND_GROUP_MESSAGE:
                    process = new ProcessBuilder("osascript", scriptFile.getAbsolutePath(), args[0], args[1], args[2], args[3], args[4], args[5]).start();
                    break;
                case RENAME_GROUP:
                    process = new ProcessBuilder("osascript", scriptFile.getAbsolutePath(), args[0], args[1], args[2], args[3]).start();
                    break;
                case ADD_PARTICIPANT:
                    process = new ProcessBuilder("osascript", scriptFile.getAbsolutePath(), args[0], args[1], args[2], args[3]).start();
                    break;
                case REMOVE_PARTICIPANT:
                    process = new ProcessBuilder("osascript", scriptFile.getAbsolutePath(), args[0], args[1], args[2], args[3]).start();
                    break;
                case CREATE_GROUP:
                    process = new ProcessBuilder("osascript", scriptFile.getAbsolutePath(), args[0], args[1], args[2]).start();
                    break;
                case LEAVE_GROUP:
                    process = new ProcessBuilder("osascript", scriptFile.getAbsolutePath(), args[0], args[1], args[2]).start();
                    break;
                default:
                    returnCode = null;
                    process = null;
                    break;
            }

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null){
                returnCode = line;
            }
            bufferedReader.close();

            if (StringUtils.isEmpty(returnCode)){
                result = ReturnType.UNKNOWN_ERROR;

                ServerLogger.log(ServerLogger.Level.SEVERE, TAG, "An unknown error occurred while running script " + actionType.getScriptName() + ".scpt");
                ServerLogger.log(ServerLogger.Level.ERROR, TAG, "In order to prevent further errors from occurring, weServer will force close and relaunch Messages.");
                ServerLogger.emptyLine();
                killMessagesApp(true);

                return result;
            }

            ArrayList<String> resultReturns = new ArrayList<>(Arrays.asList(returnCode.split(", ")));
            List<ReturnType> resultReturnsList = new ArrayList<>();

            for (String s : resultReturns) {
                resultReturnsList.add(ReturnType.fromCode(Integer.parseInt(s)));
            }

            if(resultReturnsList.isEmpty()){
                result = null;
            }else if (resultReturnsList.size() == 1){
                ReturnType returnType = resultReturnsList.get(0);
                result = returnType;

                if (returnType == ReturnType.UI_ERROR){
                    ServerLogger.log(ServerLogger.Level.ERROR, TAG, "A UI error occurred within the Messages App.");
                    ServerLogger.log(ServerLogger.Level.ERROR, TAG, "In order to prevent further errors from occurring, weServer will force close and relaunch it.");
                    ServerLogger.emptyLine();
                    killMessagesApp(true);
                }
            }else {
                result = resultReturnsList;

                for (ReturnType returnType : resultReturnsList) {
                    if (returnType == ReturnType.UI_ERROR) {
                        ServerLogger.log(ServerLogger.Level.ERROR, TAG, "A UI error occurred within the Messages App.");
                        ServerLogger.log(ServerLogger.Level.ERROR, TAG, "In order to prevent further errors from occurring, weServer will force close and relaunch it.");
                        ServerLogger.emptyLine();
                        killMessagesApp(true);
                        break;
                    }
                }
            }
        }catch (Exception ex){
            ServerLogger.error(TAG, "An error occurred while running script " + actionType.getScriptName() + ".scpt", ex);
            result = null;
        }
        return result;
    }

    public boolean isSetup(){
        File setupScriptFile;

        try {
            File[] scriptFiles = getScriptsFolder().listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.startsWith("Setup");
                }
            });
            setupScriptFile = scriptFiles[0];
        }catch(Exception ex){
            ServerLogger.error(TAG, "The script Setup.scpt does not exist!", new NullPointerException());
            return false;
        }

        try {
            Process process = new ProcessBuilder("osascript", setupScriptFile.getAbsolutePath(), String.valueOf(weMessage.WEMESSAGE_APPLESCRIPT_VERSION)).start();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int returnCode = -1;

            while ((line = bufferedReader.readLine()) != null){
                returnCode = Integer.parseInt(line);
            }
            bufferedReader.close();

            if(returnCode == ReturnType.ACTION_PERFORMED.getCode()){
                return true;
            }else if (returnCode == ReturnType.VERSION_MISMATCH.getCode()){
                ServerLogger.log(ServerLogger.Level.ERROR, TAG, "The weServer version and the scripts version do not match!");
                ServerLogger.log(ServerLogger.Level.ERROR, TAG, "Make sure you are using the correct scripts for your version.");
                ServerLogger.log(ServerLogger.Level.ERROR, TAG, "You may have to re-download your weServer package in order to get the right ones.");
                return false;
            }else {
                ServerLogger.log(ServerLogger.Level.ERROR, TAG, "weServer is not configured to run yet.");
                ServerLogger.log(ServerLogger.Level.ERROR, TAG, "Make sure that assistive access is enabled!");
                return false;
            }
        }catch(Exception ex){
            ServerLogger.error("An error occurred while checking to see if weServer is configured to start.", ex);
            return false;
        }
    }

    public void startMessagesApp() {
        File helperScriptFile;

        try {
            File[] scriptFiles = getScriptsFolder().listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.startsWith("Helpers");
                }
            });
            helperScriptFile = scriptFiles[0];
        }catch(Exception ex){
            ServerLogger.error(TAG, "The script Helpers.scpt does not exist!", new NullPointerException());
            return;
        }

        try {
            new ProcessBuilder("osascript", helperScriptFile.getAbsolutePath(), String.valueOf(1)).start();
        }catch(Exception ex){
            ServerLogger.error("An error occurred while starting the Messages app.", ex);
        }
    }

    public void killMessagesApp(boolean respring) {
        File helperScriptFile;

        try {
            File[] scriptFiles = getScriptsFolder().listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.startsWith("Helpers");
                }
            });
            helperScriptFile = scriptFiles[0];
        }catch(Exception ex){
            ServerLogger.error(TAG, "The script Helpers.scpt does not exist!", new NullPointerException());
            return;
        }

        try {
            new ProcessBuilder("osascript", helperScriptFile.getAbsolutePath(), String.valueOf(2)).start();
        }catch(Exception ex){
            ServerLogger.error("An error occurred while closing the Messages app.", ex);
        }

        if (respring){
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    startMessagesApp();
                }
            }, 200);
        }
    }

    public boolean runContactSync() {
        UUID uuid = UUID.randomUUID();
        AtomicBoolean shouldLoop = new AtomicBoolean(true);
        queue.add(uuid);

        while (shouldLoop.get()){
            if (queue.get(0).equals(uuid)){
                queue.remove(uuid);
                shouldLoop.set(false);
            }
        }

        File contactSyncFile;
        ServerConfiguration serverConfiguration = getMessageServer().getConfiguration();

        new File(serverConfiguration.getParentDirectoryPath(), "contacts").mkdir();

        try {
            File[] scriptFiles = getScriptsFolder().listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.startsWith("ContactSync");
                }
            });
            contactSyncFile = scriptFiles[0];
        }catch(Exception ex){
            ServerLogger.error(TAG, "The script ContactSync.scpt does not exist!", new NullPointerException());
            return false;
        }

        try {
            Process process = new ProcessBuilder("osascript", contactSyncFile.getAbsolutePath(), String.valueOf(serverConfiguration.getConfigJSON().getConfig().getSyncContactPhotos())).start();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int returnCode = -1;

            while ((line = bufferedReader.readLine()) != null){
                returnCode = Integer.parseInt(line);
            }
            bufferedReader.close();

            if(returnCode == ReturnType.ACTION_PERFORMED.getCode()){
                return true;
            }else {
                ServerLogger.log("An error occurred while performing a contact sync.");
                return false;
            }

        }catch(Exception ex){
            ServerLogger.error("An error occurred while attempting to sync your contacts.", ex);
            return false;
        }
    }
}