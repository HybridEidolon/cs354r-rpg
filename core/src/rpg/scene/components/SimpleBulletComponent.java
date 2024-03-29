package rpg.scene.components;

import com.badlogic.gdx.math.Vector3;
import rpg.scene.Node;
import rpg.scene.NodeFactory;
import rpg.scene.replication.Context;
import rpg.scene.replication.Replicated;
import rpg.scene.systems.NetworkingSceneSystem;
import rpg.scene.systems.Node2DQuerySystem;

import java.util.Objects;
import java.util.Set;

/**
 * Created by Corin Hill on 5/6/15.
 */
public class SimpleBulletComponent extends Component implements Steppable {

    protected static float MOVE_SPEED = 8;
    protected static float LIFETIME = 2;

    @Replicated
    protected float age = 0;
    @Replicated
    protected Vector3 moveDirection = new Vector3();

    private Vector3 oldPosition = null;
    private Vector3 newPosition = null;

    private float moveTimer = 0;
    private boolean lerpTargetChanged = false;

    @Replicated
    protected Node creator;

    private UnitComponent creatorUnitComponent;

    private boolean shadowCreated;

    public SimpleBulletComponent() {

    }

    @Override
    public void step(float deltaTime) {
        NetworkingSceneSystem nss = getParent().getScene().findSystem(NetworkingSceneSystem.class);
        Objects.requireNonNull(nss);

        Transform t = getParent().getTransform();
        Vector3 p = t.getPosition().cpy();

        if (nss.getContext() == Context.Server) {
            if (age < LIFETIME) {
                t.setPosition(p.add(moveDirection.cpy().scl(deltaTime)));
                checkCollisions();
                age += deltaTime;
            } else {
                Node n = getParent();
                n.getParent().removeChild(n);
            }
        } else if (nss.getContext() == Context.Client) {
            if (age < LIFETIME) {
                moveTimer += deltaTime;
                if (lerpTargetChanged) {
                    if (oldPosition != null) {
                        newPosition = p.cpy().add(p.cpy().sub(oldPosition));
                    }
                    oldPosition = p.cpy();
                    moveTimer = 0;
                    lerpTargetChanged = false;
                }

                if (oldPosition != null && newPosition != null) {
                    // Extrapolate server side motion
                    t.setPosition(oldPosition.cpy().lerp(newPosition, moveTimer / nss.getTickDeltaTime()));
                } else {
                    t.setPosition(p.add(moveDirection.cpy().scl(deltaTime)));
                }
                age += deltaTime;

                // add shadow if needed
                if (!shadowCreated) {
                    Node n = NodeFactory.makeShadowNode(getParent(), true);
                    n.getTransform().translate(0, 0, -1.5f + 0.005f);
                    n.getTransform().setScale(new Vector3(0.3f, 0.3f, 0.3f));
                    shadowCreated = true;
                }
            }
        }
    }

    protected void checkCollisions() {
        Node2DQuerySystem n2qs = getParent().getScene().findSystem(Node2DQuerySystem.class);
        Objects.requireNonNull(n2qs);
        Spatial2D s = getParent().findComponent(Spatial2D.class);
        Objects.requireNonNull(s);

        Vector3 worldPosition = getParent().getTransform().getWorldPosition();

        Set<Node> nodes = n2qs.queryNodesInArea(s.getRectangle().setCenter(worldPosition.x, worldPosition.y));
        nodes.stream()
                .map(n -> n.findComponent(UnitComponent.class))
                .forEach(c -> {
                    if (c == null) return;
                    if (getParent() == null || getParent().getParent() == null) return;
                    if (c.getFaction() != creatorUnitComponent.getFaction()) {
                        c.hurt(this, 10);
                        getParent().getParent().removeChild(getParent());
                    }
                });
    }

    @Override
    public void onPostApplyReplicatedFields() {
        lerpTargetChanged = true;
    }
    @Override
    public boolean isAlwaysFieldReplicated() {return true;}

    public Vector3 getMoveDirection() {return moveDirection;}
    public void setMoveDirection(Vector3 v) { moveDirection = v.cpy().nor().scl(MOVE_SPEED);}

    public Node getCreator() {
        return creator;
    }

    public void setCreator(Node creator) {
        this.creator = creator;
        this.creatorUnitComponent = creator.findComponent(UnitComponent.class);
    }
}