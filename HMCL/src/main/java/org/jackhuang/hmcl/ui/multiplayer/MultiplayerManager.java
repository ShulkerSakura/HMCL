/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.multiplayer;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.launch.StreamPump;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Lang.wrap;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 * Cato Management.
 */
public final class MultiplayerManager {
    static final String CATO_VERSION = "1.1.1";
    //    private static final String CATO_DOWNLOAD_URL = "https://files.huangyuhui.net/maven/cato/cato/" + MultiplayerManager.CATO_VERSION;
    private static final String CATO_DOWNLOAD_URL = "https://codechina.csdn.net/huanghongxun1/ioi_bin/-/raw/3f36a567d9381e5fa404b96bcecd6d6a555f235c/client/";
    private static final String CATO_PATH = getCatoPath();
    public static final int CATO_AGREEMENT_VERSION = 2;
    private static final String REMOTE_ADDRESS = "127.0.0.1";
    private static final String LOCAL_ADDRESS = "0.0.0.0";

    private static final Map<String, String> HASH = mapOf(
            pair("cato-client-darwin-amd64", "85951cbae62f46d7011eceb18b5997fb"),
            pair("cato-client-darwin-arm64", "421cb801dfcba614c4f14b7919e8e2a5"),
            pair("cato-client-freebsd-amd64", "cd01352a0728d207975bb86d3da31013"),
            pair("cato-client-freebsd-arm64", "b9e23809800cab9abc137cb72b0da357"),
            pair("cato-client-freebsd-arm7", "b5f01984415dba54394848325cecb12d"),
            pair("cato-client-freebsd-i386", "5895d27ed62d0db112ebbeb21986999a"),
            pair("cato-client-js.wasm", "82db2736c458b5ad0d8c41d36f084631"),
            pair("cato-client-linux-amd64", "3e8e87c7199f9e915c04adb075a137b0"),
            pair("cato-client-linux-arm64", "cd6f9dda26985b590b9c9677ac77badf"),
            pair("cato-client-linux-arm7", "03ce55bb35fff033a7aa79e4602b0a46"),
            pair("cato-client-linux-i386", "8af4e4c016af60222812442a48c906b8"),
            pair("cato-client-linux-mips", "ca698df658127c1f222701dd925c4ea8"),
            pair("cato-client-linux-mips64", "7abe28e1b63b2d44cd85cc1ba6872db8"),
            pair("cato-client-linux-mips64le", "f590b1258ec97ee304063a7058b9898d"),
            pair("cato-client-linux-mipsle", "7bc49eed31b5f9185da490c77097f98a"),
            pair("cato-client-linux-ppc64", "2e82979331635c05aecd6de975f6f38f"),
            pair("cato-client-linux-ppc64le", "8631285fcd4e6a43221a2a52d07833da"),
            pair("cato-client-openbsd-amd64", "8a10b9e4746e3ac83c92b0451bc0ee99"),
            pair("cato-client-openbsd-arm64", "f4fabec0132b09324cd7512ab77969d0"),
            pair("cato-client-openbsd-arm7", "66c43fb6b42dd71d15eb0328af0d1d3e"),
            pair("cato-client-openbsd-i386", "a9f4d15135d9295ac96498e91182b124"),
            pair("cato-client-windows-amd64.exe", "69638ed3d93251d8ad041ce453e375d7"),
            pair("cato-client-windows-arm64.exe", "05c104b0e7568524ecdcb92b1c9318e5"),
            pair("cato-client-windows-i386.exe", "6ef47ffde88a0d97425fbab67cb5c20e")
    );

    private MultiplayerManager() {
    }

    public static Task<Void> downloadCato() {
        return new FileDownloadTask(
                NetworkUtils.toURL(CATO_DOWNLOAD_URL + getCatoFileName()),
                getCatoExecutable().toFile(),
                new FileDownloadTask.IntegrityCheck("SHA-1", HASH.get(getCatoFileName()))
        ).thenRunAsync(() -> {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                Set<PosixFilePermission> perm = Files.getPosixFilePermissions(getCatoExecutable());
                perm.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(getCatoExecutable(), perm);
            }
        });
    }

    public static Path getCatoExecutable() {
        return Metadata.HMCL_DIRECTORY.resolve("libraries").resolve(CATO_PATH);
    }

    private static CompletableFuture<CatoSession> startCato(String token, State state) {
        return CompletableFuture.completedFuture(null).thenApplyAsync(wrap(unused -> {
            Path exe = getCatoExecutable();
            if (!Files.isRegularFile(exe)) {
                throw new CatoNotExistsException(exe);
            }

            if (!isPortAvailable(3478)) {
                throw new CatoAlreadyStartedException();
            }

            try {
                ChecksumMismatchException.verifyChecksum(exe, "SHA-1", HASH.get(getCatoFileName()));
            } catch (IOException e) {
                Files.deleteIfExists(exe);
                throw e;
            }

            String[] commands = new String[]{exe.toString(), "--token", StringUtils.isBlank(token) ? "new" : token};
            Process process = new ProcessBuilder()
                    .command(commands)
                    .start();

            return new CatoSession(state, process, Arrays.asList(commands));
        }));
    }

    public static CompletableFuture<CatoSession> joinSession(String token, String peer, Mode mode, int remotePort, int localPort, JoinSessionHandler handler) throws IncompatibleCatoVersionException {
        LOG.info(String.format("Joining session (token=%s,peer=%s,mode=%s,remotePort=%d,localPort=%d)", token, peer, mode, remotePort, localPort));

        return startCato(token, State.SLAVE).thenComposeAsync(wrap(session -> {
            CompletableFuture<CatoSession> future = new CompletableFuture<>();

            session.forwardPort(peer, LOCAL_ADDRESS, localPort, REMOTE_ADDRESS, remotePort, mode);

            Consumer<CatoExitEvent> onExit = event -> {
                boolean ready = session.isReady();
                switch (event.getExitCode()) {
                    case 1:
                        if (!ready) {
                            future.completeExceptionally(new CatoExitTimeoutException());
                        }
                        break;
                }
                future.completeExceptionally(new CatoExitException(event.getExitCode(), ready));
            };
            session.onExit.register(onExit);

            TimerTask peerConnectionTimeoutTask = Lang.setTimeout(() -> {
                future.completeExceptionally(new PeerConnectionTimeoutException());
                session.stop();
            }, 15 * 1000);

            session.onPeerConnected.register(event -> {
                peerConnectionTimeoutTask.cancel();

                MultiplayerClient client = new MultiplayerClient(session.getId(), localPort);
                session.addRelatedThread(client);
                session.setClient(client);

                TimerTask task = Lang.setTimeout(() -> {
                    future.completeExceptionally(new JoinRequestTimeoutException());
                    session.stop();
                }, 30 * 1000);

                client.onConnected().register(connectedEvent -> {
                    try {
                        int port = findAvailablePort();
                        session.forwardPort(peer, LOCAL_ADDRESS, port, REMOTE_ADDRESS, connectedEvent.getPort(), mode);
                        session.addRelatedThread(Lang.thread(new LocalServerBroadcaster(port, session), "LocalServerBroadcaster", true));
                        session.setName(connectedEvent.getSessionName());
                        client.setGamePort(port);
                        session.onExit.unregister(onExit);
                        future.complete(session);
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                        session.stop();
                    }
                    task.cancel();
                });
                client.onKicked().register(kickedEvent -> {
                    future.completeExceptionally(new KickedException(kickedEvent.getReason()));
                    session.stop();
                    task.cancel();
                });
                client.onDisconnected().register(disconnectedEvent -> {
                    if (!client.isConnected()) {
                        // We fail to establish connection with server
                        future.completeExceptionally(new ConnectionErrorException());
                    }
                });
                client.onHandshake().register(handshakeEvent -> {
                    if (handler != null) {
                        handler.onWaitingForJoinResponse();
                    }
                });
                client.start();
            });

            return future;
        }));
    }

    public static CompletableFuture<CatoSession> createSession(String token, String sessionName, int gamePort, boolean allowAllJoinRequests) {
        LOG.info(String.format("Creating session (token=%s,sessionName=%s,gamePort=%d)", token, sessionName, gamePort));

        return startCato(token, State.MASTER).thenComposeAsync(wrap(session -> {
            CompletableFuture<CatoSession> future = new CompletableFuture<>();

            MultiplayerServer server = new MultiplayerServer(sessionName, gamePort, allowAllJoinRequests);
            server.startServer();

            session.setName(sessionName);
            session.allowForwardingAddress(REMOTE_ADDRESS, server.getPort());
            session.allowForwardingAddress(REMOTE_ADDRESS, gamePort);
            session.showAllowedAddress();

            Consumer<CatoExitEvent> onExit = event -> {
                boolean ready = session.isReady();
                switch (event.getExitCode()) {
                    case 1:
                        if (!ready) {
                            future.completeExceptionally(new CatoExitTimeoutException());
                        }
                        break;
                }
                future.completeExceptionally(new CatoExitException(event.getExitCode(), ready));
            };

            session.onExit.register(onExit);
            session.setServer(server);
            session.addRelatedThread(server);

            TimerTask peerConnectionTimeoutTask = Lang.setTimeout(() -> {
                future.completeExceptionally(new PeerConnectionTimeoutException());
                session.stop();
            }, 15 * 1000);

            session.onPeerConnected.register(event -> {
                session.onExit.unregister(onExit);
                future.complete(session);
                peerConnectionTimeoutTask.cancel();
            });

            return future;
        }));
    }

    public static final Pattern INVITATION_CODE_PATTERN = Pattern.compile("^(?<id>.*?)#(?<port>\\d{2,5})$");

    public static Invitation parseInvitationCode(String invitationCode) throws JsonParseException {
        Matcher matcher = INVITATION_CODE_PATTERN.matcher(invitationCode);
        if (!matcher.find()) throw new IllegalArgumentException("Invalid invitation code");
        return new Invitation(matcher.group("id"), Integer.parseInt(matcher.group("port")));
    }

    public static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String getCatoFileName() {
        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS:
                if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                    return "cato-client-windows-amd64.exe";
                } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                    return "cato-client-windows-arm64.exe";
                } else if (Architecture.SYSTEM_ARCH == Architecture.X86) {
                    return "cato-client-windows-i386.exe";
                } else {
                    return "";
                }
            case OSX:
                if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                    return "cato-client-darwin-amd64";
                } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                    return "cato-client-darwin-arm64";
                } else {
                    return "";
                }
            case LINUX:
                if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                    return "cato-client-linux-amd64";
                } else if (Architecture.SYSTEM_ARCH == Architecture.ARM32) {
                    return "cato-client-linux-arm7";
                } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                    return "cato-client-linux-arm64";
                } else {
                    return "";
                }
            default:
                return "";
        }
    }

    public static String getCatoPath() {
        String name = getCatoFileName();
        if (StringUtils.isBlank(name)) return "";
        return "cato/cato/" + MultiplayerManager.CATO_VERSION + "/" + name;
    }

    public static class CatoSession extends ManagedProcess {
        private final EventManager<CatoExitEvent> onExit = new EventManager<>();
        private final EventManager<CatoIdEvent> onIdGenerated = new EventManager<>();
        private final EventManager<Event> onPeerConnected = new EventManager<>();

        private String name;
        private final State type;
        private String id;
        private boolean peerConnected = false;
        private MultiplayerClient client;
        private MultiplayerServer server;
        private final BufferedWriter writer;

        CatoSession(State type, Process process, List<String> commands) {
            super(process, commands);

            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            LOG.info("Started cato with command: " + new CommandBuilder().addAll(commands));

            this.type = type;
            addRelatedThread(Lang.thread(this::waitFor, "CatoExitWaiter", true));
            addRelatedThread(Lang.thread(new StreamPump(process.getInputStream(), this::checkCatoLog), "CatoInputStreamPump", true));
            addRelatedThread(Lang.thread(new StreamPump(process.getErrorStream(), this::checkCatoLog), "CatoErrorStreamPump", true));

            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        }

        public MultiplayerClient getClient() {
            return client;
        }

        public CatoSession setClient(MultiplayerClient client) {
            this.client = client;
            return this;
        }

        public MultiplayerServer getServer() {
            return server;
        }

        public CatoSession setServer(MultiplayerServer server) {
            this.server = server;
            return this;
        }

        private void checkCatoLog(String log) {
            LOG.info("Cato: " + log);
            if (id == null) {
                Matcher matcher = TEMP_TOKEN_PATTERN.matcher(log);
                if (matcher.find()) {
                    id = matcher.group("id");
                    onIdGenerated.fireEvent(new CatoIdEvent(this, id));
                }
            }

            if (!peerConnected) {
                Matcher matcher = PEER_CONNECTED_PATTERN.matcher(log);
                if (matcher.find()) {
                    peerConnected = true;
                    onPeerConnected.fireEvent(new Event(this));
                }
            }
        }

        private void waitFor() {
            try {
                int exitCode = getProcess().waitFor();
                LOG.info("cato exited with exitcode " + exitCode);
                onExit.fireEvent(new CatoExitEvent(this, exitCode));
            } catch (InterruptedException e) {
                onExit.fireEvent(new CatoExitEvent(this, CatoExitEvent.EXIT_CODE_INTERRUPTED));
            } finally {
                try {
                    writer.close();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to close cato stdin writer", e);
                }
            }
            destroyRelatedThreads();
        }

        public boolean isReady() {
            return id != null;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public State getType() {
            return type;
        }

        @Nullable
        public String getId() {
            return id;
        }

        public String generateInvitationCode(int serverPort) {
            if (id == null) {
                throw new IllegalStateException("id not generated");
            }
            return id + "#" + serverPort;
        }

        public synchronized void invokeCommand(String command) throws IOException {
            LOG.info("Invoking cato: " + command);
            writer.write(command);
            writer.newLine();
            writer.flush();
        }

        public void forwardPort(String peerId, String localAddress, int localPort, String remoteAddress, int remotePort, Mode mode) throws IOException {
            invokeCommand(String.format("net add %s %s:%d %s:%d %s", peerId, localAddress, localPort, remoteAddress, remotePort, mode.getName()));
        }

        public void allowForwardingAddress(String address, int port) throws IOException {
            invokeCommand(String.format("ufw net open %s:%d", address, port));
        }

        public void showAllowedAddress() throws IOException {
            invokeCommand("ufw net whitelist");
        }

        public EventManager<CatoExitEvent> onExit() {
            return onExit;
        }

        public EventManager<CatoIdEvent> onIdGenerated() {
            return onIdGenerated;
        }

        public EventManager<Event> onPeerConnected() {
            return onPeerConnected;
        }

        private static final Pattern TEMP_TOKEN_PATTERN = Pattern.compile("id\\((?<id>\\w+)\\)");
        private static final Pattern PEER_CONNECTED_PATTERN = Pattern.compile("Connected to main net");
        private static final Pattern LOG_PATTERN = Pattern.compile("(\\[\\d+])\\s+(\\w+)\\s+(\\w+-{0,1}\\w+):\\s(.*)");
    }

    public static class CatoExitEvent extends Event {
        private final int exitCode;

        public CatoExitEvent(Object source, int exitCode) {
            super(source);
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }

        public static final int EXIT_CODE_INTERRUPTED = -1;
        public static final int EXIT_CODE_SESSION_EXPIRED = 10;
    }

    public static class CatoIdEvent extends Event {
        private final String id;

        public CatoIdEvent(Object source, String id) {
            super(source);
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    enum State {
        DISCONNECTED,
        CONNECTING,
        MASTER,
        SLAVE
    }

    public static class Invitation {
        private final String id;
        @SerializedName("p")
        private final int channelPort;

        public Invitation(String id, int channelPort) {
            this.id = id;
            this.channelPort = channelPort;
        }

        public String getId() {
            return id;
        }

        public int getChannelPort() {
            return channelPort;
        }
    }

    public interface JoinSessionHandler {
        void onWaitingForJoinResponse();
    }

    public static class IncompatibleCatoVersionException extends Exception {
        private final String expected;
        private final String actual;

        public IncompatibleCatoVersionException(String expected, String actual) {
            this.expected = expected;
            this.actual = actual;
        }

        public String getExpected() {
            return expected;
        }

        public String getActual() {
            return actual;
        }
    }

    public enum Mode {
        P2P,
        BRIDGE;

        String getName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static class CatoExitException extends RuntimeException {
        private final int exitCode;
        private final boolean ready;

        public CatoExitException(int exitCode, boolean ready) {
            this.exitCode = exitCode;
            this.ready = ready;
        }

        public int getExitCode() {
            return exitCode;
        }

        public boolean isReady() {
            return ready;
        }
    }

    public static class CatoExitTimeoutException extends RuntimeException {
    }

    public static class CatoSessionExpiredException extends RuntimeException {
    }

    public static class CatoAlreadyStartedException extends RuntimeException {
    }

    public static class JoinRequestTimeoutException extends RuntimeException {
    }

    public static class PeerConnectionTimeoutException extends RuntimeException {
    }

    public static class ConnectionErrorException extends RuntimeException {
    }

    public static class KickedException extends RuntimeException {
        private final String reason;

        public KickedException(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    public static class CatoNotExistsException extends RuntimeException {
        private final Path file;

        public CatoNotExistsException(Path file) {
            this.file = file;
        }

        public Path getFile() {
            return file;
        }
    }
}
