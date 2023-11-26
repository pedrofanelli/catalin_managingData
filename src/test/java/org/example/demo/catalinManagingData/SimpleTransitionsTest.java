package org.example.demo.catalinManagingData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.FlushModeType;
import javax.persistence.Persistence;
import javax.persistence.PersistenceUnitUtil;

import org.example.demo.models.Item;
import org.hibernate.LazyInitializationException;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;



public class SimpleTransitionsTest {

	/**
	 * Todos estos test corren uno después del otro, no se resetea la base en cada método
	 * Sólo se resetea en cada corrida de test
	 */
	
	private static EntityManagerFactory emf =
            Persistence.createEntityManagerFactory("Chapter10");

    @Test
    public void makePersistent() {
        EntityManager em = emf.createEntityManager(); // Application-managed

        em.getTransaction().begin();
        Item item = new Item();
        item.setName("Some Item qwer"); // Item#name is NOT NULL!
        em.persist(item);

        Long ITEM_ID = item.getId();

        em.getTransaction().commit();
        em.close();

        em = emf.createEntityManager();
        em.getTransaction().begin();
        assertEquals("Some Item qwer", em.find(Item.class, ITEM_ID).getName());
        em.getTransaction().commit();
        em.close();

    }
    
    @Test
    public void retrievePersistent() throws Exception {
        EntityManager em = emf.createEntityManager(); // Application-managed
        em.getTransaction().begin();
        Item someItem = new Item();
        someItem.setName("Some Item");
        em.persist(someItem);
        em.getTransaction().commit();
        em.close();
        Long ITEM_ID = someItem.getId();

        {
            em = emf.createEntityManager();
            em.getTransaction().begin();
            // Hit the database if not already in persistence context
            Item item = em.find(Item.class, ITEM_ID);
            if (item != null)
                item.setName("New Name");
            em.getTransaction().commit(); // Flush: Dirty check and SQL UPDATE
            em.close();
        }

        {
            em = emf.createEntityManager();
            em.getTransaction().begin();

            Item itemA = em.find(Item.class, ITEM_ID);
            Item itemB = em.find(Item.class, ITEM_ID); // Repeatable read

            assertTrue(itemA == itemB);
            assertTrue(itemA.equals(itemB));
            assertTrue(itemA.getId().equals(itemB.getId()));

            em.getTransaction().commit(); // Flush: Dirty check and SQL UPDATE
            em.close();
        }

        em = emf.createEntityManager();
        em.getTransaction().begin();
        assertEquals("New Name", em.find(Item.class, ITEM_ID).getName());
        em.getTransaction().commit();
        em.close();
    }
    
    @Test
    public void retrievePersistentReference() {
        EntityManager em = emf.createEntityManager(); // Application-managed
        em.getTransaction().begin();
        Item someItem = new Item();
        someItem.setName("Some Item 265");
        em.persist(someItem);
        em.getTransaction().commit();
        em.close();
        Long ITEM_ID = someItem.getId();

        em = emf.createEntityManager();
        em.getTransaction().begin();

            /*
               If the persistence context already contains an <code>Item</code> with the given identifier, that
               <code>Item</code> instance is returned by <code>getReference()</code> without hitting the database.
               Furthermore, if <em>no</em> persistent instance with that identifier is currently managed, a hollow
               placeholder will be produced by Hibernate, a proxy. This means <code>getReference()</code> will not
               access the database, and it doesn't return <code>null</code>, unlike <code>find()</code>.
             */
        Item item = em.getReference(Item.class, ITEM_ID);

            /*
               JPA offers <code>PersistenceUnitUtil</code> helper methods such as <code>isLoaded()</code> to
               detect if you are working with an uninitialized proxy.
            */
        PersistenceUnitUtil persistenceUtil =
                emf.getPersistenceUnitUtil();
        assertFalse(persistenceUtil.isLoaded(item));

            /*
               As soon as you call any method such as <code>Item#getName()</code> on the proxy, a
               <code>SELECT</code> is executed to fully initialize the placeholder. The exception to this rule is
               a method that is a mapped database identifier getter method, such as <code>getId()</code>. A proxy
               might look like the real thing but it is only a placeholder carrying the identifier value of the
               entity instance it represents. If the database record doesn't exist anymore when the proxy is
               initialized, an <code>EntityNotFoundException</code> will be thrown.
             */
        // assertEquals("Some Item", item.getName());
            /*
               Hibernate has a convenient static <code>initialize()</code> method, loading the proxy's data.
             */
//         Hibernate.initialize(item);

        em.getTransaction().commit();
        em.close();

            /*
               After the persistence context is closed, <code>item</code> is in detached state. If you do
               not initialize the proxy while the persistence context is still open, you get a
               <code>LazyInitializationException</code> if you access the proxy. You can't load
               data on-demand once the persistence context is closed. The solution is simple: Load the
               data before you close the persistence context.
             */
        assertThrows(LazyInitializationException.class, () -> item.getName());
    }
    
    @Test
    public void makeTransient() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Item someItem = new Item();
        someItem.setName("Some Item to transient");
        em.persist(someItem);
        em.getTransaction().commit();
        em.close();
        Long ITEM_ID = someItem.getId();

        em = emf.createEntityManager();
        em.getTransaction().begin();
            /*
               If you call <code>find()</code>, Hibernate will execute a <code>SELECT</code> to
               load the <code>Item</code>. If you call <code>getReference()</code>, Hibernate
               will attempt to avoid the <code>SELECT</code> and return a proxy.
             */
        Item item = em.find(Item.class, ITEM_ID);
        //Item item = em.getReference(Item.class, ITEM_ID);

            /*
               Calling <code>remove()</code> will queue the entity instance for deletion when
               the unit of work completes, it is now in <em>removed</em> state. If <code>remove()</code>
               is called on a proxy, Hibernate will execute a <code>SELECT</code> to load the data.
               An entity instance has to be fully initialized during life cycle transitions. You may
               have life cycle callback methods or an entity listener enabled
               (see <a href="#EventListenersInterceptors"/>), and the instance must pass through these
               interceptors to complete its full life cycle.
             */
        em.remove(item);

            /*
                An entity in removed state is no longer in persistent state, this can be
                checked with the <code>contains()</code> operation.
             */
        assertFalse(em.contains(item));

            /*
               You can make the removed instance persistent again, cancelling the deletion.
             */
        // em.persist(item);

        // hibernate.use_identifier_rollback was enabled, it now looks like a transient instance
        assertNull(item.getId());

            /*
               When the transaction commits, Hibernate synchronizes the state transitions with the
               database and executes the SQL <code>DELETE</code>. The JVM garbage collector detects that the
               <code>item</code> is no longer referenced by anyone and finally deletes the last trace of
               the data.
             */
        em.getTransaction().commit();
        em.close();

        em = emf.createEntityManager();
        em.getTransaction().begin();

        item = em.find(Item.class, ITEM_ID);
        assertNull(item);
        em.getTransaction().commit();
        em.close();
    }
    
    @Test
    public void refresh() throws ExecutionException, InterruptedException {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Item someItem = new Item();
        someItem.setName("Some Item");
        em.persist(someItem);
        em.getTransaction().commit();
        em.close();
        Long ITEM_ID = someItem.getId();

        em = emf.createEntityManager();
        em.getTransaction().begin();

        Item item = em.find(Item.class, ITEM_ID);
        item.setName("Some Name");

        // Someone updates this row in the database!
        Executors.newSingleThreadExecutor().submit(() -> {
            EntityManager em1 = emf.createEntityManager();
            try {
                em1.getTransaction().begin();

                Session session = em1.unwrap(Session.class);
                session.doWork(con -> {
                    Item item1 = em1.find(Item.class, ITEM_ID);
                    item1.setName("Concurrent Update Name");
                    em1.persist(item1);
                });

                em1.getTransaction().commit();
                em1.close();

            } catch (Exception ex) {
                throw new RuntimeException("Concurrent operation failure: " + ex, ex);
            }
            return null;
        }).get();

        em.refresh(item);
        em.getTransaction().commit(); // Flush: Dirty check and SQL UPDATE

        em.refresh(item);
        em.close();
        assertEquals("Concurrent Update Name", item.getName());
    }
    
    @Test
    public void flushModeType() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Item someItem = new Item();
        someItem.setName("Original Name");
        em.persist(someItem);
        em.getTransaction().commit();
        em.close();
        Long ITEM_ID = someItem.getId();

        em = emf.createEntityManager();
        em.getTransaction().begin();

        Item item = em.find(Item.class, ITEM_ID);
        item.setName("New Name flushed");

        // Disable flushing before queries:
        em.setFlushMode(FlushModeType.COMMIT);

        assertEquals(
                "Original Name",
                em.createQuery("select i.name from Item i where i.id = :id", String.class)
                        .setParameter("id", ITEM_ID).getSingleResult()
        );

        em.getTransaction().commit(); // Flush!
        em.close();
    }
    
    @Test
    public void scopeOfIdentity() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Item someItem = new Item();
        someItem.setName("Some Item");
        em.persist(someItem);
        em.getTransaction().commit();
        em.close();
        Long ITEM_ID = someItem.getId();

        em = emf.createEntityManager();
        em.getTransaction().begin();

        Item a = em.find(Item.class, ITEM_ID);
        Item b = em.find(Item.class, ITEM_ID);
        assertTrue(a == b);
        assertTrue(a.equals(b));
        assertEquals(a.getId(), b.getId());

        em.getTransaction().commit();
        em.close();
        // Persistence Context is gone, 'a' and 'b' are now references to instances in detached state!

        em = emf.createEntityManager();
        em.getTransaction().begin();

        Item c = em.find(Item.class, ITEM_ID);
        assertTrue(a != c); // The 'a' reference is still detached!
        assertFalse(a.equals(c));
        assertEquals(a.getId(), c.getId());

        em.getTransaction().commit();
        em.close();

        Set<Item> allItems = new HashSet<>();
        allItems.add(a);
        allItems.add(b);
        allItems.add(c);
        assertEquals(2, allItems.size()); // That seems wrong and arbitrary!

    }
}
