package rpg.scene.replication;

import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.Test;
import rpg.scene.components.Component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RepTableTest {

    class Reppable {
        @Replicated
        protected Vector2 hiddenVec = new Vector2(0, 0);
    }

    class SimpleReppable extends Reppable {
        @Replicated
        protected Vector2 vec = new Vector2(0, 0);

        @Replicated
        protected Vector2 anotherVec = new Vector2(1, 1);
    }

    class SimpleHasAPrivateReplicated {
        @Replicated
        private Vector2 thisShouldExcept = new Vector2(0, 0);
    }

    class HasAnRPC extends Component {
        @RPC(target = RPC.Target.Client)
        protected void run() {
            testValue = 5;
        }

        public int testValue = 0;
    }

    @Test
    public void testSimpleReplication() {
        RepTable.discardAllRepTables();
        RepTable t = RepTable.getTableForType(SimpleReppable.class);

        assertNotNull(t);
        SimpleReppable simpleReppable = new SimpleReppable();
        simpleReppable.vec = new Vector2(123, 456);
        simpleReppable.anotherVec = new Vector2(543, 212);

        FieldReplicationData frd = t.replicateFull(simpleReppable);

        assertNotNull(frd);

        assertEquals("The changeset BitSet should hold 8 values (1 byte).", 8, frd.fieldChangeset.getSize());

        assertEquals("A full replication should have as many data objects as the size of the BitSet",
                3, frd.fieldData.size());

        assertEquals(simpleReppable.vec, frd.fieldData.get(1));
        assertEquals(simpleReppable.anotherVec, frd.fieldData.get(2));
    }

    @Test
    public void testDeltaReplication() {
        RepTable.discardAllRepTables();
        RepTable t = RepTable.getTableForType(SimpleReppable.class);

        assertNotNull(t);
        SimpleReppable simpleReppable = new SimpleReppable();
        simpleReppable.vec = new Vector2(123, 456);
        simpleReppable.anotherVec = new Vector2(543, 212);

        FieldReplicationData frdOld = t.replicateFull(simpleReppable);

        // Some small changes...
        simpleReppable.vec = new Vector2(321, 812);

        FieldReplicationData frdNew = t.replicateFull(simpleReppable);

        // Diff them
        FieldReplicationData frdDiff = frdOld.diff(frdNew);

        assertEquals("Only one property should have changed", 1, frdDiff.fieldChangeset.cardinality());
        assertEquals(new Vector2(321, 812), frdDiff.fieldData.get(0));
    }

    @Test
    public void testDeltaReplicationApplication() {
        RepTable.discardAllRepTables();
        RepTable t = RepTable.getTableForType(SimpleReppable.class);

        assertNotNull(t);
        SimpleReppable simpleReppable = new SimpleReppable();
        simpleReppable.vec = new Vector2(123, 456);
        simpleReppable.anotherVec = new Vector2(543, 212);

        FieldReplicationData frdOld = t.replicateFull(simpleReppable);

        // Some small changes...
        simpleReppable.vec = new Vector2(321, 812);

        FieldReplicationData frdNew = t.replicateFull(simpleReppable);

        // Diff them
        FieldReplicationData frdDiff = frdOld.diff(frdNew);

        t.applyReplicationData(frdDiff, simpleReppable);

        assertEquals(new Vector2(321, 812), simpleReppable.vec);
        assertEquals(new Vector2(543, 212), simpleReppable.anotherVec);
    }

    @Test
    public void testFieldDataKryoSerialization() {
        RepTable.discardAllRepTables();
        RepTable t = RepTable.getTableForType(SimpleReppable.class);

        SimpleReppable simpleReppable = new SimpleReppable();

        Kryo k = new Kryo();
        k.register(Vector2.class);
        k.register(BitSet.class);
        k.register(FieldReplicationData.class);

        FieldReplicationData frdOld = t.replicateFull(simpleReppable);
        simpleReppable.vec = new Vector2(156, 812);
        FieldReplicationData frdNew = t.replicateFull(simpleReppable);

        FieldReplicationData frdDelta = frdOld.diff(frdNew);
        simpleReppable.vec = new Vector2(0, 0); // reset to original state

        byte[] buf = new byte[32];
        Output output = new Output();
        output.setBuffer(buf);

        Input input = new Input();
        input.setBuffer(buf);

        k.writeObject(output, frdDelta);

        FieldReplicationData frdSerial = k.readObject(input, FieldReplicationData.class);

        assertEquals(frdDelta, frdSerial);
    }

    @Test(expected = RuntimeException.class)
    public void testNoPrivateFieldsAllowed() throws Exception {
        RepTable.discardAllRepTables();
        RepTable t = RepTable.getTableForType(SimpleHasAPrivateReplicated.class);
    }


    @Test
    public void testRPCMethodsExist() throws Exception {
        RepTable.discardAllRepTables();
        RepTable t = RepTable.getTableForType(HasAnRPC.class);

        int i = t.getRPCMethodID("run");
        // An exception will be thrown if it doesn't exist.
    }

    @Test
    public void testRPCMethodCreateRPCMessage() throws Exception {
        RepTable.discardAllRepTables();
        RepTable t = RepTable.getTableForType(HasAnRPC.class);

        RPCInvocation r = t.getRPCInvocation("run");
        RPCInvocation exp = new RPCInvocation();
        exp.methodId = 0;
        assertEquals(r, exp);
    }
}
