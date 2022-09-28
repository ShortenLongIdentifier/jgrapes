/*
 * JGrapes Event Driven Framework
 * Copyright (C) 2016, 2018  Michael N. Lipp
 *
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Affero General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License 
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package org.jgrapes.util.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jdrupes.json.JsonBeanDecoder;
import org.jdrupes.json.JsonDecodeException;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.util.JsonConfigurationStore;
import org.jgrapes.util.events.ConfigurationUpdate;
import org.jgrapes.util.events.InitialConfiguration;
import static org.junit.Assert.*;
import org.junit.Test;

public class JsonConfigTests {

    public class UpdateTrigger extends Event<Void> {
    }

    public class App extends Component {

        @SuppressWarnings("unchecked")
        @Handler
        public void onConfigurationUpdate(ConfigurationUpdate event) {
            if (event instanceof InitialConfiguration) {
                assertEquals("42", event.value("/", "answer").get());
                assertEquals("24", event.value("/sub/tree", "value").get());
                assertEquals("1", event.value("/sub/tree", "list.0").get());
                assertEquals("1", event.value("/sub/tree", "map.one").get());
                var list = (List<Number>) event.structured("/sub/tree").get()
                    .get("list");
                assertEquals(3, list.size());
                for (int i = 1; i <= 3; i++) {
                    assertEquals(i, list.get(i - 1).intValue());
                }
                var map = (Map<String, Object>) event.structured("/sub/tree")
                    .get().get("map");
                assertEquals(1, ((Number) map.get("one")).intValue());
                assertEquals(2,
                    ((List<Number>) map.get("more")).get(0).intValue());
                assertEquals(3,
                    ((List<Number>) map.get("more")).get(1).intValue());
            }
        }

        @Handler
        public void onTriggerUpdate(UpdateTrigger event) {
            fire(new ConfigurationUpdate().add("/", "updated", "new")
                .add("/sub/tree", "map.more.1", "4"));
        }
    }

    public class Child extends Component {

        public int value = 0;

        @Handler
        public void onConfigurationUpdate(ConfigurationUpdate event) {
            event.value("/", "answer", Number.class)
                .ifPresent(it -> value = it.intValue());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMisc() throws InterruptedException,
            UnsupportedEncodingException, FileNotFoundException, IOException,
            JsonDecodeException {

        // Create app and initial file
        App app = new App();
        File file = new File("testConfig.json");
        try (Writer out
            = new OutputStreamWriter(new FileOutputStream(file), "utf-8")) {
            out.write("{\"answer\":42, \"/sub\":{\"/tree\":{\"value\":24,"
                + "\"list\":[1,2,3],\"map\":{\"one\":1,\"more\":[2,3]}}}}");
        }
        var conf = new JsonConfigurationStore(app, file);

        // Check direct access to store
        assertEquals("42", conf.values("/").get().get("answer"));
        assertEquals("24", conf.values("/sub/tree").get().get("value"));
        assertEquals("1", conf.values("/sub/tree").get().get("list.0"));
        assertEquals("1", conf.values("/sub/tree").get().get("map.one"));
        var list
            = (List<Number>) conf.structured("/sub/tree").get().get("list");
        assertEquals(3, list.size());
        for (int i = 1; i <= 3; i++) {
            assertEquals(i, list.get(i - 1).intValue());
        }
        var map = (Map<String, Object>) conf.structured("/sub/tree").get()
            .get("map");
        assertEquals(1, ((Number) map.get("one")).intValue());
        assertEquals(2, ((List<Number>) map.get("more")).get(0).intValue());
        assertEquals(3, ((List<Number>) map.get("more")).get(1).intValue());
        app.attach(conf);

        Components.start(app);
        Components.awaitExhaustion();
        Components.checkAssertions();

        // Does attached child get (initial) configuration?
        Child child = new Child();
        app.attach(child);
        Components.awaitExhaustion();
        Components.checkAssertions();
        assertEquals(42, child.value);

        // Check storage update
        app.fire(new UpdateTrigger(), app);
        Components.awaitExhaustion();
        Components.checkAssertions();
        assertEquals("4", conf.values("/sub/tree").get().get("map.more.1"));

        Map<String, Object> root;
        try (Reader input
            = new InputStreamReader(new FileInputStream(file), "utf-8")) {
            root = JsonBeanDecoder.create(input)
                .readObject(HashMap.class);
        }

        // File must have been updated
        assertEquals("new", root.get("updated"));

        // Remove sub tree event
        app.fire(new ConfigurationUpdate().removePath("/sub"), app);
        Components.awaitExhaustion();
        Components.checkAssertions();
        try (Reader input
            = new InputStreamReader(new FileInputStream(file), "utf-8")) {
            root = JsonBeanDecoder.create(input).readObject(HashMap.class);
        }

        // Sub tree must have been removed in file
        assertFalse(root.containsKey("/sub"));

        // Remove test preferences
        app.fire(new ConfigurationUpdate().removePath("/"), app);
        Components.awaitExhaustion();
        Components.checkAssertions();
        try (Reader input
            = new InputStreamReader(new FileInputStream(file), "utf-8")) {
            root = JsonBeanDecoder.create(input).readObject(HashMap.class);
        }

        // Data must have been removed
        assertTrue(root.isEmpty());

        // Cleanup
        file.delete();
    }

}
