package com.roni.life.os.app.quickball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class NativeActionRegistryTest {

    private NativeActionRegistry registry;

    @Before
    public void setUp() {
        registry = NativeActionRegistry.get();
        registry.reset();
    }

    private NativeAction action(String id, NativeAction.Category category) {
        return new NativeAction.Builder(id, id, (activity, project) -> {
        }).category(category).build();
    }

    @Test
    public void duplicateRegistrationIsRejected() {
        assertTrue(registry.register(action("qb.one", NativeAction.Category.TOOLS)));
        assertFalse(registry.register(action("qb.one", NativeAction.Category.SYSTEM)));
        assertEquals(1, registry.size());
        assertEquals(NativeAction.Category.TOOLS, registry.find("qb.one").getCategory());
    }

    @Test
    public void unregisterRemovesOnlyTheRequestedId() {
        registry.register(action("qb.one", NativeAction.Category.TOOLS));
        registry.register(action("qb.two", NativeAction.Category.FILES));
        assertTrue(registry.unregister("qb.one"));
        assertFalse(registry.unregister("qb.one"));
        assertNull(registry.find("qb.one"));
        assertNotNull(registry.find("qb.two"));
    }

    @Test
    public void categoryFilterReturnsOnlyMatchingActions() {
        registry.register(action("qb.task", NativeAction.Category.AUTOMATION));
        registry.register(action("qb.alarm", NativeAction.Category.AUTOMATION));
        registry.register(action("qb.file", NativeAction.Category.FILES));
        assertEquals(2, registry.getByCategory(NativeAction.Category.AUTOMATION).size());
        assertEquals(1, registry.getByCategory(NativeAction.Category.FILES).size());
        assertTrue(registry.getByCategory(NativeAction.Category.MEDIA).isEmpty());
    }

    @Test
    public void registrationOrderIsPreserved() {
        registry.register(action("qb.a", NativeAction.Category.TOOLS));
        registry.register(action("qb.b", NativeAction.Category.TOOLS));
        registry.register(action("qb.c", NativeAction.Category.TOOLS));
        assertEquals(java.util.Arrays.asList("qb.a", "qb.b", "qb.c"), registry.getIds());
    }

    @Test
    public void customLabelAndIconDoNotMutateTheOriginal() {
        NativeAction original = new NativeAction.Builder("qb.one", "Original",
                (activity, project) -> {
                }).icon(1).build();
        NativeAction renamed = original.withLabel("Custom").withIcon(2);
        assertEquals("Original", original.getLabel());
        assertEquals(1, original.getIconRes());
        assertEquals("Custom", renamed.getLabel());
        assertEquals(2, renamed.getIconRes());
        assertEquals(original.getId(), renamed.getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankIdIsRejected() {
        new NativeAction.Builder("  ", "Label", (activity, project) -> {
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankLabelIsRejected() {
        new NativeAction.Builder("qb.one", " ", (activity, project) -> {
        });
    }
}
