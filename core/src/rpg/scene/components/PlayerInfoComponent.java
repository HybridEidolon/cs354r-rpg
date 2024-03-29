package rpg.scene.components;

import com.badlogic.gdx.Input;
import com.esotericsoftware.minlog.Log;
import rpg.scene.Scene;
import rpg.scene.replication.RPC;
import rpg.scene.replication.Replicated;
import rpg.scene.systems.InputSystem;
import rpg.scene.systems.Scene2DUISystem;
import rpg.ui.InGameUIScreen;
import rpg.ui.UIScreen;

import java.util.Objects;

public class PlayerInfoComponent extends Component implements InputEventListener {

    @Replicated
    protected String name = "Unnamed";

    public PlayerInfoComponent() {
        this("Unnamed");
    }

    public PlayerInfoComponent(String name) {
        super();
        this.name = name;
    }

    @RPC(target = RPC.Target.Server)
    protected void sendChatMessage(String message) {
        Objects.requireNonNull(message);
        if (message.length() > 100) {
            return;
        }
        if (message.length() == 0) {
            return;
        }

        Log.info("Say", name + ": " + message);

        sendRPC("chatMessage", message);
    }

    @RPC(target = RPC.Target.Multicast)
    protected void chatMessage(String message) {
        addMessageToChatbox(String.format("%s: %s", getPlayerName(), message), InGameUIScreen.MessageType.Say);
    }

    @RPC(target = RPC.Target.Client)
    protected void systemMessage(String message) {
        addMessageToChatbox(message, InGameUIScreen.MessageType.System);
    }

    @RPC(target = RPC.Target.Client)
    protected void gameMessage(String message) {
        addMessageToChatbox(message, InGameUIScreen.MessageType.Game);
    }

    protected void addMessageToChatbox(String message, InGameUIScreen.MessageType type) {
        Scene s = getParent().getScene();
        Scene2DUISystem ui = s.findSystem(Scene2DUISystem.class);
        if (ui != null) {
            UIScreen ss = ui.getScreen();
            if (ss instanceof InGameUIScreen) {
                InGameUIScreen inGameUIScreen = ((InGameUIScreen) ss);
                inGameUIScreen.addChatMessage(message, type);
            }
        }
    }

    public String getPlayerName() {
        return name;
    }

    public void setPlayerName(String name) {
        this.name = name;
    }

    @Override
    public void processInputEvent(InputSystem.InputEvent event) {
        if (event.getType() == InputSystem.EventType.KeyDown) {
            if (event.getButton() == Input.Keys.ENTER) {
                Scene s = getParent().getScene();
                Scene2DUISystem uiSystem = s.findSystem(Scene2DUISystem.class);
                if (uiSystem != null) {
                    UIScreen uiScreen = uiSystem.getScreen();
                    if (uiScreen instanceof InGameUIScreen) {
                        InGameUIScreen inGameUIScreen = ((InGameUIScreen) uiScreen);
                        inGameUIScreen.focusChatEntry();
                    }
                }
            }
        }
    }
}
