package rpg.scene.components;

import com.badlogic.gdx.graphics.Color;
import rpg.scene.Node;

/**
 * Created by Corin Hill on 5/6/15.
 */
public class SimpleEnemySpawnComponent extends SpawnComponent {
    public SimpleEnemySpawnComponent() {
        frequency = 15;
    }
    @Override
    protected void setFrequency(float frequency) {
        this.frequency = frequency;
    }

    @Override
    protected float getFrequency() {
        return frequency;
    }
    @Override
    protected void spawn() {
        Node enemyNode = new Node();
        getParent().getScene().getRoot().addChild(enemyNode);

        SimpleEnemyComponent s = new SimpleEnemyComponent();
        RectangleRenderer r = new RectangleRenderer();
        r.setColor(Color.MAROON);
        enemyNode.addComponent(s);
        enemyNode.addComponent(r);

        Transform tEnemy = enemyNode.getTransform();
        Transform tSelf = getParent().getTransform();

        tEnemy.setPosition(tSelf.getWorldPosition());
        tEnemy.setRotation(tSelf.getWorldRotation());
    }
}
