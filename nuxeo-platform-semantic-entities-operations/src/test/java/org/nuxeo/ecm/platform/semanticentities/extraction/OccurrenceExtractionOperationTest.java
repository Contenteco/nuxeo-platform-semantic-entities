/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 */
package org.nuxeo.ecm.platform.semanticentities.extraction;

import java.io.Serializable;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.semanticentities.Constants;
import org.nuxeo.ecm.platform.semanticentities.LocalEntityService;
import org.nuxeo.ecm.platform.semanticentities.RemoteEntityService;
import org.nuxeo.ecm.platform.semanticentities.SemanticAnalysisService;
import org.nuxeo.runtime.api.Framework;

public class OccurrenceExtractionOperationTest extends SQLRepositoryTestCase {

    private DocumentModel john;

    private DocumentModel johndoe;

    private DocumentModel beatles;

    private DocumentModel liverpool;

    private LocalEntityService leService;

    private RemoteEntityService reService;

    private SemanticAnalysisService saService;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // necessary for the fulltext indexer and text extraction for analysis
        deployBundle("org.nuxeo.ecm.core.convert.api");
        deployBundle("org.nuxeo.ecm.core.convert");
        deployBundle("org.nuxeo.ecm.core.convert.plugins");

        // semantic entities types
        deployBundle("org.nuxeo.ecm.platform.semanticentities.core");

        // CMIS query maker
        deployBundle("org.nuxeo.ecm.core.opencmis.impl");

        // initialize the session field
        openSession();
        DocumentModel domain = session.createDocumentModel("/", "default-domain", "Folder");
        session.createDocument(domain);
        session.save();

        leService = Framework.getService(LocalEntityService.class);
        assertNotNull(leService);

        reService = Framework.getService(RemoteEntityService.class);
        assertNotNull(reService);

        saService = Framework.getService(SemanticAnalysisService.class);
        assertNotNull(saService);
    }

    public void makeSomeEntities() throws ClientException {
        DocumentModel container = leService.getEntityContainer(session);
        assertNotNull(container);
        assertEquals(Constants.ENTITY_CONTAINER_TYPE, container.getType());

        john = session.createDocumentModel(container.getPathAsString(), null, "Person");
        john.setPropertyValue("dc:title", "John Lennon");
        john.setPropertyValue("entity:summary", "John Winston Ono Lennon, MBE (9 October 1940 – 8 December 1980)"
                + " was an English rock musician, singer-songwriter, author, and peace"
                + " activist who gained worldwide fame as one of the founding members of" + " The Beatles.");
        john.setPropertyValue("entity:sameas", (Serializable) Arrays.asList("http://dbpedia.org/resource/John_Lennon"));
        john.setPropertyValue("entity:sameasDisplayLabel", (Serializable) Arrays.asList("John Lennon"));
        john.setPropertyValue("entity:types", (Serializable) Arrays.asList("http://dbpedia.org/ontology/MusicalArtist"));
        john.setPropertyValue("person:birthDate", new GregorianCalendar(1940, 10, 9));
        john.setPropertyValue("person:birthDate", new GregorianCalendar(1980, 12, 8));
        john = session.createDocument(john);

        // add another john
        johndoe = session.createDocumentModel(container.getPathAsString(), null, "Person");
        johndoe.setPropertyValue("dc:title", "John Doe");
        johndoe = session.createDocument(johndoe);

        beatles = session.createDocumentModel(container.getPathAsString(), null, "Organization");
        beatles.setPropertyValue("dc:title", "The Beatles");
        beatles.setPropertyValue("entity:summary", "The Beatles were an English rock band, formed in Liverpool in 1960"
                + " and one of the most commercially successful and critically acclaimed"
                + " acts in the history of popular music.");

        beatles.setPropertyValue("entity:sameas",
                (Serializable) Arrays.asList("http://dbpedia.org/resource/The_Beatles"));
        beatles.setPropertyValue("entity:sameasDisplayLabel", (Serializable) Arrays.asList("The Beatles"));
        beatles.setPropertyValue("entity:types", (Serializable) Arrays.asList("http://dbpedia.org/ontology/Band"));
        beatles = session.createDocument(beatles);

        liverpool = session.createDocumentModel(container.getPathAsString(), null, "Place");
        liverpool.setPropertyValue("dc:title", "Liverpool");
        liverpool.setPropertyValue("entity:summary",
                "Liverpool is a city and metropolitan borough of Merseyside, England, along"
                        + " the eastern side of the Mersey Estuary. It was founded as a borough"
                        + " in 1207 and was granted city status in 1880.");

        liverpool.setPropertyValue("entity:sameas",
                (Serializable) Arrays.asList("http://dbpedia.org/resource/Liverpool"));
        liverpool.setPropertyValue("entity:sameasDisplayLabel", (Serializable) Arrays.asList("http://Liverpool"));
        liverpool.setPropertyValue("entity:types", (Serializable) Arrays.asList("http://dbpedia.org/ontology/City"));
        liverpool.setPropertyValue("place:latitude", 53.4);
        liverpool.setPropertyValue("place:longitude", -2.983);
        liverpool = session.createDocument(liverpool);
        session.save();
        Framework.getLocalService(EventService.class).waitForAsyncCompletion();
    }

    public DocumentModel createSampleDocumentModel() throws ClientException {
        DocumentModel doc = session.createDocumentModel("/", "john-bio", "Note");
        doc.setPropertyValue("dc:title", "A short bio for John Lennon");
        doc.setPropertyValue("note:note", "<html><body>" + "<h1>This is an HTML title</h1>"
                + "<p>John Lennon was born in Liverpool in 1940. John was a musician."
                + " This document about John Lennon has many occurrences"
                + " of the words 'John' and 'Lennong' hence should rank high"
                + " for suggestions on such keywords.</p>"

                + "<!-- this is a HTML comment about Bob Marley. -->" + " </body></html>");
        doc = session.createDocument(doc);
        session.save(); // force write to SQL backend
        Framework.getLocalService(EventService.class).waitForAsyncCompletion(1000 * 10);
        return doc;
    }

    @Test
    public void testOperationDirectCall() throws Exception {
        DocumentModel doc = createSampleDocumentModel();
        // TODO: use a derived operation that mocks the service HTTP interaction
        OccurrenceExtractionOperation op = new OccurrenceExtractionOperation(session);

        DocumentModel resultDoc = op.run(doc);
        assertNotNull(resultDoc);
        assertEquals(doc.getRef(), resultDoc.getRef());

        PageProvider<DocumentModel> relatedPeople = leService.getRelatedEntities(session, doc.getRef(), "Person");
        List<DocumentModel> firstPeople = relatedPeople.getCurrentPage();
        assertEquals(1, firstPeople.size());
        assertEquals("John Lennon", firstPeople.get(0).getTitle());

        PageProvider<DocumentModel> relatedPlaces = leService.getRelatedEntities(session, doc.getRef(), "Place");
        List<DocumentModel> firstPlaces = relatedPlaces.getCurrentPage();
        assertEquals(1, firstPlaces.size());
        assertEquals("Liverpool", firstPlaces.get(0).getTitle());
    }

    @Test
    public void testCoreEventListener() throws Exception {
        // TODO: deploy a the mock HTTP operation instead of the default core
        // event listener
        deployBundle("org.nuxeo.ecm.platform.semanticentities.operations");

        DocumentModel doc = createSampleDocumentModel();
        Framework.getLocalService(EventService.class).waitForAsyncCompletion();
        while (saService.getProgressStatus(doc.getRepositoryName(), doc.getRef()) != null) {
            Thread.sleep(100);
        }

        PageProvider<DocumentModel> relatedPeople = leService.getRelatedEntities(session, doc.getRef(), "Person");
        List<DocumentModel> firstPeople = relatedPeople.getCurrentPage();
        assertEquals(1, firstPeople.size());
        assertEquals("John Lennon", firstPeople.get(0).getTitle());

        PageProvider<DocumentModel> relatedPlaces = leService.getRelatedEntities(session, doc.getRef(), "Place");
        List<DocumentModel> firstPlaces = relatedPlaces.getCurrentPage();
        assertEquals(1, firstPlaces.size());
        assertEquals("Liverpool", firstPlaces.get(0).getTitle());
    }
}
