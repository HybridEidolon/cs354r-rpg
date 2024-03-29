package rpg.scene.components;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.esotericsoftware.minlog.Log;
import rpg.scene.Node;
import rpg.scene.NodeFactory;
import rpg.scene.replication.Context;
import rpg.scene.replication.RPC;
import rpg.scene.replication.Replicated;
import rpg.scene.systems.InputSystem.InputEvent;
import rpg.scene.systems.NetworkingSceneSystem;

import static rpg.scene.systems.InputSystem.EventType.*;

public class SimplePlayerComponent extends Component implements Steppable, InputEventListener, Killable {

    private boolean mouseLEFT;

    private float shootTimer = 0;
    private static float SHOOT_UPDATE_THRESHOLD = 1f / 8;

    private Vector2 mousePosition;

    public Vector2 animMoveDirection = new Vector2();

    private boolean keyW;
    private boolean keyS;
    private boolean keyA;
    private boolean keyD;
    private boolean keyE;

    private static float MOVE_SPEED = 4;
    private static float MOVE_SPEED_SQUARED = 16;

    private float moveTimer = 0;
    private static float MOVE_UPDATE_THRESHOLD = 1.f / 30;

    private Vector3 oldPosition;
    private Vector3 newPosition;

    private Vector3 clientRealPosition = null;

    private boolean lerpTargetChanged = false;

    private UnitComponent unitComponent;

    @Replicated
    protected PlayerInfoComponent playerInfoComponent;

    @Replicated
    protected int heldItem = 0;

    @Override
    public void processInputEvent(InputEvent event) {
        if (event.getType() == KeyDown) {
            switch (event.getButton()) {
                case Keys.W:
                    keyW = true;
                    break;
                case Keys.S:
                    keyS = true;
                    break;
                case Keys.A:
                    keyA = true;
                    break;
                case Keys.D:
                    keyD = true;
                    break;
                case Keys.E:
                    keyE = true;
                    break;
                default:
                    break;
            }
        }
        if (event.getType() == KeyUp) {
            switch (event.getButton()) {
                case Keys.W:
                    keyW = false;
                    break;
                case Keys.S:
                    keyS = false;
                    break;
                case Keys.A:
                    keyA = false;
                    break;
                case Keys.D:
                    keyD = false;
                    break;
                case Keys.E:
                    keyE = false;
                    break;
                default:
                    break;
            }
        }
        if (event.getType() == MouseButtonPressed) {
            switch (event.getButton()) {
                case Input.Buttons.LEFT:
                    mouseLEFT = true;
                    mousePosition = event.getScreenPosition().cpy();
                    break;
                default:
                    break;
            }
        }
        if (event.getType() == MouseButtonReleased) {
            switch (event.getButton()) {
                case Input.Buttons.LEFT:
                    mouseLEFT = false;
                    break;
                default:
                    break;
            }
        }
        if (event.getType() == MouseDragged) {
            if (mouseLEFT) {
                mousePosition = event.getScreenPosition().cpy();
            }
        }
    }

    @Override
    public void step(float deltaTime) {
        NetworkingSceneSystem nss = getParent().getScene().findSystem(NetworkingSceneSystem.class);

        if (unitComponent == null) {
            unitComponent = getParent().findComponent(UnitComponent.class);
        }

        if (nss == null || (nss.getContext() == Context.Client && getParent().isPossessed())) {
            Transform t = getParent().getTransform();
            if (clientRealPosition == null) {
                clientRealPosition = t.getPosition();
            }
            moveTimer += deltaTime;

            // Handle movement
            Vector3 moveDirection = new Vector3();
            if (keyW) {
                moveDirection.y += 1;
            }
            if (keyS) {
                moveDirection.y -= 1;
            }
            if (keyA) {
                moveDirection.x -= 1;
            }
            if (keyD) {
                moveDirection.x += 1;
            }

            moveDirection.nor().scl(MOVE_SPEED).scl(deltaTime);
            clientRealPosition.add(moveDirection);
            if (moveTimer >= MOVE_UPDATE_THRESHOLD) {
                sendRPC("setPlayerPosition", clientRealPosition.cpy());
                moveTimer = 0;
            }
            t.setPosition(clientRealPosition);
            this.animMoveDirection.set(moveDirection.x, moveDirection.y);

            if (mouseLEFT) {
                if (shootTimer >= SHOOT_UPDATE_THRESHOLD || shootTimer == 0) {
                    float x = mousePosition.x - (Gdx.graphics.getWidth() / 2);
                    float y = (Gdx.graphics.getHeight() / 2) - mousePosition.y;
                    if (keyE && heldItem == PickupComponent.BOMB) {
                        heldItem = 0;
                        sendRPC("generateBomb", new Vector3(x, y, 0));
                    } else {
                        sendRPC("generateBullet", new Vector3(x, y, 0));
                    }
                    shootTimer = 0;
                }
                shootTimer += deltaTime;
            } else {
                // If the shoot timer is not zero, increment and loop as normal
                // Prevents mashing producing bullets faster than holding
                if (shootTimer >= SHOOT_UPDATE_THRESHOLD) {
                    shootTimer = 0;
                }
                if (shootTimer != 0) {
                    shootTimer += deltaTime;
                }
            }

            if (keyE && heldItem == PickupComponent.HEAL) {
                sendRPC("useItem");
            }
        } else if (nss.getContext() == Context.Server) {
            // Server side
            Transform t = getParent().getTransform();

            // For interpolating movement from the client
            // The client decides what position it is in, rather than the server
            moveTimer += deltaTime;
            if (oldPosition != null) {
                if (moveTimer >= MOVE_UPDATE_THRESHOLD) {
                    oldPosition = null;
                    moveTimer = 0;
                    t.setPosition(newPosition.cpy());
                } else {
                    t.setPosition(oldPosition.cpy().lerp(newPosition, moveTimer / MOVE_UPDATE_THRESHOLD));
                }
            } else if (newPosition != null) {
                t.setPosition(newPosition);
            }

            // If we haven't found our PlayerInfoComponent, find it now.
            if (playerInfoComponent == null) {
                playerInfoComponent = getParent().findComponent(PlayerInfoComponent.class);
            }

        } else if (nss.getContext() == Context.Client) {
            Transform t = getParent().getTransform();

            if (oldPosition != null) {
                moveTimer += deltaTime;
                if (lerpTargetChanged) {
                    moveTimer = 0;
                    lerpTargetChanged = false;
                    animMoveDirection.set(newPosition.x - oldPosition.x, newPosition.y - oldPosition.y).nor();
                }

                t.setPosition(oldPosition.cpy().lerp(newPosition, moveTimer / nss.getTickDeltaTime()));
            } else if (newPosition != null) {
                t.setPosition(newPosition);
            }
        }
    }
    @RPC(target = RPC.Target.Server)
    public void useItem() {
        if (heldItem == PickupComponent.HEAL) {
            UnitComponent u = getParent().findComponent(UnitComponent.class);
            u.setHealth(u.getMaxHealth());
            heldItem = 0;
        }
    }
    @RPC(target = RPC.Target.Server)
    public void generateBullet(Vector3 v) {
        Node bulletNode = new Node();
        getParent().getScene().getRoot().addChild(bulletNode);

        v.nor();

        SimpleBulletComponent bulletComponent = new SimpleBulletComponent();
        bulletComponent.setMoveDirection(v);
        bulletComponent.setCreator(getParent());

        SpriteRenderer spriteRenderer = new SpriteRenderer();
        spriteRenderer.setTexture("sprites/bullet-player.png");
        spriteRenderer.setDimensions(new Vector2(0.5f, 0.5f));
        spriteRenderer.setRotation(45f);
        spriteRenderer.setBillboard(false);

        bulletNode.addComponent(bulletComponent);
        bulletNode.addComponent(spriteRenderer);

        Transform tBullet = bulletNode.getTransform();
        Transform tSelf = getParent().getTransform();

        tBullet.setPosition(tSelf.getWorldPosition());
        tBullet.translate(0, 0, 0.5f);
        float angle = 0;
        if (v.x >= 0) {
            // TR
            angle = (float) Math.acos(v.dot(Vector3.Y));
        } else if (v.x < 0) {
            // BL
            angle = (float) -Math.acos(v.dot(Vector3.Y));
        }
        spriteRenderer.setRotation(-angle * MathUtils.radiansToDegrees - 45f);
    }
    @RPC(target = RPC.Target.Server)
    public void generateBomb(Vector3 v) {
        heldItem = 0;

        Node bulletNode = new Node();
        getParent().getScene().getRoot().addChild(bulletNode);

        v.nor();

        MissileComponent missileComponent = new MissileComponent();
        missileComponent.setMoveDirection(v);
        missileComponent.setCreator(getParent());

        SpriteRenderer spriteRenderer = new SpriteRenderer();
        spriteRenderer.setTexture("sprites/orange.png");
        spriteRenderer.setDimensions(new Vector2(0.4f, 0.4f));
        spriteRenderer.setBillboard(false);
        bulletNode.addComponent(missileComponent);
        bulletNode.addComponent(spriteRenderer);

        Transform tBullet = bulletNode.getTransform();
        Transform tSelf = getParent().getTransform();

        tBullet.setPosition(tSelf.getWorldPosition());
        tBullet.translate(0, 0, 0.5f);
    }

    @RPC(target = RPC.Target.Server)
    public void setPlayerPosition(Vector3 vec) {
        Transform t = getParent().getTransform();
        vec.z = 0;
        if (vec.cpy().sub(t.getPosition()).len2() > MOVE_SPEED_SQUARED * 1.5f) {
            Log.warn(getClass().getSimpleName(), "Player tried to move too fast.");
        }
        oldPosition = t.getPosition().cpy();
        newPosition = vec.cpy();
        moveTimer = 0;
    }

    @Override
    public void onPreApplyReplicateFields() {
        Transform t = getParent().getTransform();
        oldPosition = newPosition == null ? null : newPosition.cpy();
        // The transform has already been updated by now.
        newPosition = t.getPosition().cpy();
        lerpTargetChanged = true;
    }

    @Override
    public boolean isAlwaysFieldReplicated() {
        return true;
    }

    @Override
    public void kill() {
        playerInfoComponent.sendRPC("gameMessage", "You have died.");
        SpriteRenderer s = getParent().findComponent(SpriteRenderer.class);
        PlayerSpriteAnimatorComponent p = getParent().findComponent(PlayerSpriteAnimatorComponent.class);
        if (s != null) getParent().removeComponent(s);
        if (p != null) getParent().removeComponent(p);

        Transform t = getParent().getTransform();
        Node n = NodeFactory.createGravestone(getParent().findRoot(), false);
        n.getTransform().setPosition(new Vector3(t.getPosition().x, t.getPosition().y, 5));
        n.getTransform().translate(0, 0, 5);

        FallToGroundComponent f = new FallToGroundComponent();
        n.addComponent(f);

        getParent().addComponent(new PlayerRespawnComponent());
        getParent().removeComponent(this);
    }

    public void pickup(int item) {
        switch (item) {
            case PickupComponent.EXP:
                UnitComponent unit = getParent().findComponent(UnitComponent.class);
                // Nice, hardcoded experience pickup
                if (unit != null) unit.setExperience(unit.getExperience() + 10);
                break;
            default:
                heldItem = item;
                break;
        }
    }

    public boolean isHoldingItem() {return heldItem != 0;}
}
