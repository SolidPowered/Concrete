package net.pizzacrust.concrete.mixin;

import net.minecraft.server.*;
import net.pizzacrust.concrete.Concrete;
import net.pizzacrust.concrete.Versioning;
import net.pizzacrust.concrete.internal.HandlerCommand;
import net.pizzacrust.concrete.internal.InternalAboutCommand;
import net.pizzacrust.concrete.internal.InternalEventTest;
import net.pizzacrust.concrete.PluginLoader;
import net.pizzacrust.concrete.SolidServer;
import net.pizzacrust.concrete.api.NetworkUser;
import net.pizzacrust.concrete.api.WorldAccessor;
import net.pizzacrust.concrete.internal.InternalPluginsCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fountainmc.api.Fountain;
import org.fountainmc.api.command.CommandManager;
import org.fountainmc.api.event.server.ServerStartEvent;
import org.fountainmc.api.event.server.ServerStopEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

@Mixin(value = MinecraftServer.class, remap = false)
public abstract class MixinMinecraftServer implements Runnable, ICommandListener, IAsyncTaskHandler, IMojangStatistics {

    @Inject(method = "t()V", at = @At(value = "RETURN", remap = false))
    private void serverReady(CallbackInfo ci) throws IOException {
        Logger logger = LogManager.getLogger("Concrete");
        NetworkUser networkUser = (NetworkUser) h();
        logger.info("Concrete (git-" + Versioning.getCommitShaVersion() + ")");
        logger.info("Latest Git Recommended Version: " + Versioning.getRemoteShaCommitVersion());
        logger.info("Network User: {}:{}", networkUser.getAddress(), networkUser.getPort());
        logger.info("API Construction is in progress...");
        MinecraftServer server = h();
        CommandDispatcher dispatcher = (CommandDispatcher) server.getCommandHandler();
        dispatcher.a(new InternalPluginsCommand());
        dispatcher.a(new InternalAboutCommand());
        SolidServer apiImpl = new SolidServer(server);
        Fountain.setServer(apiImpl);
        for (WorldServer server1 : server.worldServer) {
            server1.addIWorldAccess(new WorldAccessor());
        }
        if (!Concrete.PLUGINS_DIR.exists()) {
            Concrete.PLUGINS_DIR.mkdir();
        }
        Fountain.getServer().getPluginManager().registerListener(new InternalEventTest());
        logger.info("Plugins directory: {}", Concrete.PLUGINS_DIR.getAbsolutePath());
        logger.info("Plugin construction is in progress...");
        //apiImpl.getPluginManager().loadPlugins(Concrete.PLUGINS_DIR);
        try {
            PluginLoader.loadPlugins(Concrete.PLUGINS_DIR);
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info("Server is ready for plugin start!");
        apiImpl.getPluginManager().fireEvent(new ServerStartEvent(Fountain.getServer()));
        for (CommandManager.CommandHandler command : SolidServer.COMMAND_MANAGER.getCommands()) {
            HandlerCommand iCommand = new HandlerCommand(command);
            ((CommandDispatcher) server.getCommandHandler()).a(iCommand);
        }
    }

    @Overwrite
    public String getServerModName() {
        return "fountain-unofficial";
    }

    @Inject(method = "stop()V", at = @At(value = "HEAD", remap = false))
    private void serverStop(CallbackInfo ci) {
        Logger logger = LogManager.getLogger("Concrete");
        logger.info("Plugin construction stopping...");
        Fountain.getServer().getPluginManager().fireEvent(new ServerStopEvent());
    }
}
