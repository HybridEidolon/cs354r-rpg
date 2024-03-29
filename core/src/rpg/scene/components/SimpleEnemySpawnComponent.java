package rpg.scene.components;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import rpg.scene.Node;

/**
 * Created by Corin Hill on 5/6/15.
 */
public class SimpleEnemySpawnComponent extends SpawnComponent {
    public SimpleEnemySpawnComponent() {
        frequency = 10;
        maxSpawns = 3;
    }
    @Override
    protected Node spawn() {
        Node enemyNode = new Node();
        getParent().getScene().getRoot().addChild(enemyNode);

        SimpleEnemyComponent s = new SimpleEnemyComponent();
        SpriteRenderer spriteRenderer = new SpriteRenderer();
        UnitComponent u = new UnitComponent();
        spriteRenderer.setTexture("sprites/enemy-simple.png");
        spriteRenderer.setDimensions(new Vector2(0.5f, 0.5f));
        u.setFaction(UnitComponent.ENEMY);
        enemyNode.addComponent(s);
        enemyNode.addComponent(spriteRenderer);
        enemyNode.addComponent(u);
        spriteRenderer.setOffset(new Vector2(0, 0.5f));

        Transform tEnemy = enemyNode.getTransform();
        Transform tSelf = getParent().getTransform();

        tEnemy.setPosition(tSelf.getWorldPosition().cpy().add(new Vector3(MathUtils.random(-3f, 3f), MathUtils.random(-3f, 3f), 0)));
        //tEnemy.translate(0, 0, 0.5f);

        tEnemy.setRotation(tSelf.getWorldRotation());

        s.setHomePosition(tEnemy.getWorldPosition());

        return enemyNode;
    }
}
