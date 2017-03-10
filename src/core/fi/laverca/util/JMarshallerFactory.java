/* ==========================================
 * Laverca Project
 * https://sourceforge.net/projects/laverca/
 * ==========================================
 * Copyright 2015 Laverca Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fi.laverca.util;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.xml.sax.helpers.DefaultHandler;


/**
 * Helper routines for instantiating JAXB Marshaller and Unmarshaller of things.
 * These provide context shared instance of Mapping data so that it should
 * be read only once.
 * <p>
 * These work from Oracle Java/OpenJDK 8 onwards.
 * These MAY work also from Java 7 onwards, but never tested there.
 */
public class JMarshallerFactory {

    protected static final Log log = LogFactory.getLog(JMarshallerFactory.class);

    private static NSPfxMapper nsp;

    /**
     * JAXB RI NamespacePrefixMapper, Oracle Java 8 JRE
     */
    private static class NSPfxMapper extends com.sun.xml.internal.bind.marshaller.NamespacePrefixMapper  {

        private HashMap<String,String> uri2pfx = new HashMap<>();
        private HashMap<String,String> pfx2uri = new HashMap<>();
        private HashMap<String,String[]> uriInclusions = new HashMap<>();

        public NSPfxMapper() {
            // Nothing to do.
        }

        @Override
        public String getPreferredPrefix( final String nsUri,
                                          final String suggestion,
                                          final boolean requirePrefix )
        {
            if (nsUri == null || "".equals(nsUri)) {
                return null;
            }

            final String pfx;
            synchronized (this) {
                pfx = this.uri2pfx.get(nsUri);
            }
            if (log.isDebugEnabled()) {
                if (suggestion != null) {
                    log.debug("getPreferredPrefix('"+nsUri+"', '"+suggestion+"') -> '"+pfx+"'");
                } else {
                    log.debug("getPreferredPrefix('"+nsUri+"', null) -> '"+pfx+"'");
                }
            }
            if (pfx != null) return pfx;
            if (suggestion != null) {
                synchronized (this) {
                    this.uri2pfx.put(nsUri, suggestion);
                }
            }
            if (suggestion == null && requirePrefix) {
                // Must generate something!
                log.debug("FIXME: Must generate something!");
            }
            return suggestion;
        }

        public void setNamespaceMapping(String prefix, String uri) {
            this.uri2pfx.put(uri, prefix);
            this.pfx2uri.put(prefix, uri);
        }

        /**
         * Register a prefix to have additional inclusion prefixes.
         * Does actually register URI to inclusion URIs and uses external URI->prefix mapping.
         *
         * @param prefix
         * @param additionals
         */
        public void setInclusion(String prefix, String...additionals) {
            final String keyuri = this.pfx2uri.get(prefix);
            final String[] adds = new String[additionals.length];
            for (int i = 0; i < additionals.length; ++i) {
                final String uri = this.pfx2uri.get(additionals[i]);
                if (uri != null) {
                    adds[i] = uri;
                } else {
                    // Should not be!
                    adds[i] = additionals[i];
                }
            }
            this.uriInclusions.put(keyuri, adds);
        }

        /**
         *
         * @param nsuri Master entry namespace
         * @return null if no inclusion set, otherwise a String[].
         */
//        public String[] getInclusions(String nsuri) {
//            return this.uriInclusions.get(nsuri);
//        }
    }

    static {
        nsp = new NSPfxMapper();

        nsp.setNamespaceMapping("ds",   "http://www.w3.org/2000/09/xmldsig#");
        nsp.setNamespaceMapping("dss",  "urn:oasis:names:tc:dss:1.0:core:schema");
        nsp.setNamespaceMapping("ilink","http://www.comptel.com/ilink/api/soap/2005/09");
        nsp.setNamespaceMapping("mmd",  "http://www.methics.fi/MSSPMetadata/v1.0.0#");
        nsp.setNamespaceMapping("mreg", "http://www.methics.fi/MSSPRegistration/v1.0.0#");
        nsp.setNamespaceMapping("msrs", "http://uri.etsi.org/TS102207/v1.1.2#");
        nsp.setNamespaceMapping("mss",  "http://uri.etsi.org/TS102204/v1.1.2#");
        nsp.setNamespaceMapping("saml", "urn:oasis:names:tc:SAML:2.0:assertion");
        nsp.setNamespaceMapping("samlp", "urn:oasis:names:tc:SAML:2.0:protocol");
        nsp.setNamespaceMapping("spml",  "urn:oasis:names:tc:SPML:2:0");
        nsp.setNamespaceMapping("spmlsearch", "urn:oasis:names:tc:SPML:2:0:search");
        nsp.setNamespaceMapping("spmlref", "urn:oasis:names:tc:SPML:2:0:reference");
        nsp.setNamespaceMapping("wsa",  "http://www.w3.org/2005/08/addressing");
        nsp.setNamespaceMapping("wsse", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd");
        nsp.setNamespaceMapping("xenc", "http://www.w3.org/2001/04/xmlenc#");
        nsp.setNamespaceMapping("xs",   "http://www.w3.org/2001/XMLSchema");
        nsp.setNamespaceMapping("xsi",  "http://www.w3.org/2001/XMLSchema-instance");
    }

    /** Externally used interface matching the one at Axis ContextSerializer */
    public static void registerPrefix(final String prefix, final String uri) {
        nsp.setNamespaceMapping(prefix, uri);
    }

    /**
     *
     * @param prefix
     * @param additionals
     */
    public static void registerPrefixInclusion( String prefix, String... additionals ) {
        nsp.setInclusion(prefix, additionals);
    }

    private static void setNamespaceMappings(final Marshaller m)
        throws JAXBException
    {
        try {
            m.setProperty("com.sun.xml.internal.bind.namespacePrefixMapper",nsp);
        } catch (PropertyException e) {
            throw new JAXBException(e);
        }
    }

    /**
     * Concurrent access/modify storage of JAXBContext objects.
     */
    private static Map<Class<?>,JAXBContextDelayedLoad> jaxbCache = new ConcurrentHashMap<>();
    
    private static JAXBContext globalJAXBContext;
    private static List<String> globalJAXBPaths = new ArrayList<>();

    public static void addJAXBPath(String p) {
        globalJAXBPaths.add(p);
    }

    private static class JAXBContextDelayedLoad {
        private final Class<?> clazz;
        
        public JAXBContextDelayedLoad(final Class<?> clazz) {
            this.clazz = clazz;
        }
        
        public synchronized JAXBContext get()
            throws JAXBException
        {
            // Fast part: Return the context value if it already exists
            if (globalJAXBContext != null) return globalJAXBContext;
            try {
                StringBuilder sb = new StringBuilder();
                String colon = "";
                for (String s: globalJAXBPaths) {
                    sb.append(colon);
                    colon = ":";
                    sb.append(s);
                }
                // Slow part under synchronization: create the JAXBContext.
                globalJAXBContext = JAXBContext.newInstance(sb.toString());
                return globalJAXBContext;
            } catch (JAXBException e) {
                log.error("Instantiating JAXBContext failed for class: "+this.clazz, e);
                throw e;
            }
        }
    }

    /**
     * Get Class specific JAXBContext - either read from cache, or parse and store to cache and return.
     * For each Class the system should have exactly one JAXBContext value.
     * <p>
     * It is possible that the JAXBContext.newInstance(clazz) is invoked multiple times in parallel,
     * but eventually it should become stored in one of its instances, and there after that one will
     * be used for all needs.
     * <p>
     * The JAXBContext is thread safe, it is then used to instantiate Marshaller/Unmarshaller,
     * which are not thread safe themselves.
     */
    private static JAXBContext getJAXBContext(final Class<?> clazz)
        throws JAXBException
    {
        JAXBContextDelayedLoad ret = jaxbCache.get(clazz);
        if (ret == null) {
            ret = new JAXBContextDelayedLoad(clazz);
            jaxbCache.put(clazz,  ret);
        }
        return ret.get();
    }

    /**
     * Register given class for contained JAXB metadata parsing
     */
    public static void registerForJAXBContext(final Class<?> clazz)
    {
        final JAXBContextDelayedLoad d2 = jaxbCache.get(clazz);
        if (d2 == null) {
            jaxbCache.put(clazz, new JAXBContextDelayedLoad(clazz));
        }
    }

    /**
     * Make unmarshaller with globally shared mapping database
     *
     * @param clazz Expected unmarshalling output class.
     *              It must be pointing to a class with JAXB {{@XmlRootElement}} annotation.
     * @throws JAXBException base type for JAXB exceptions, many possible reasons
     */
    public static Unmarshaller createUnmarshaller(final Class<?> clazz)
        throws JAXBException
    {
        final JAXBContext jctx = getJAXBContext(clazz);
        final Unmarshaller   m = jctx.createUnmarshaller();
        return m;
    }

    /**
     * @param clazz A JAXB @XmlRootElement annotated class reference.
     * @return JAXB Marshaller instance
     * @throws JAXBException base type for JAXB exceptions, many possible reasons
     */
    public static Marshaller createMarshaller(Class<? extends Object> clazz)
        throws JAXBException
    {
        final JAXBContext jctx = getJAXBContext(clazz);
        final Marshaller     m = jctx.createMarshaller();
        JMarshallerFactory.setNamespaceMappings(m);
        return m;
    }

    /**
     * Marshal given object using JAXB Marshaller to SAX(2) DefaultHandler.
     *
     * @param hand A SAX2 Event Handler receiving serialization events.
     * @param value A JAXB @XmlRootElement annotated object instance.
     */
    public static void marshal( final Object value, final DefaultHandler hand)
       throws JAXBException
    {
        final Marshaller m = JMarshallerFactory.createMarshaller(value.getClass());

        // Don't include the DOCTYPE, otherwise an exception occurs due to
        // two DOCTYPEs defined in the document. The XML fragment is included
        // in an XML document containing already a DOCTYPE.
        m.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

        // Marshall the Jaxb object into the stream (sink)
        if (log.isDebugEnabled()) {
            log.debug("Running marshal(value,handler);  value="+value);
        }
        m.marshal(value, hand);
    }

    /**
     * @param object A JAXB @XmlRootElement annotated object instance.
     * @param writer A Writer instance receiving marshalling output as characters
     * @throws JAXBException base type for JAXB exceptions, many possible reasons
     */
    public static void marshal(final Object object, final Writer writer)
        throws JAXBException
    {
        final Marshaller m = JMarshallerFactory.createMarshaller(object.getClass());
        m.marshal(object, writer);
    }

    /**
     * Marshall the JAXB object to a String with XML headers and all.
     *
     * @param object A JAXB @XmlRootElement annotated object instance.
     * @return Marshalled string representation of the input data
     * @throws JAXBException base type for JAXB exceptions, many possible reasons
     */
    public static String toString(final Object object)
        throws JAXBException
    {
        final StringWriter writer = new StringWriter();
        JMarshallerFactory.marshal(object, writer);
        return writer.toString();
    }


    /**
     * Convert source data (W3C DOM Element) to JAXB data type specified
     * by the Class reference instance.
     *
     * @param javaType Conversion target type as a Class.
     * @param elt A W3C DOM data source
     * @return Converted type
     * @throws JAXBException base type for JAXB exceptions, many possible reasons
     */
    public static Object unmarshal(final Class<?> javaType, final Element elt)
        throws JAXBException
    {
        final JAXBContext jctx = getJAXBContext(javaType);
        final Unmarshaller unm = jctx.createUnmarshaller();
        return unm.unmarshal(elt);
    }

    /**
     * Convert source data to JAXB data type specified
     * by the Class reference instance.
     *
     * @param javaType Conversion target type as a Class.
     * @param reader A character reader for input source.
     * @return Converted type
     * @throws JAXBException base type for JAXB exceptions, many possible reasons
     */
    public static Object unmarshal(final Class<?> javaType, final Reader reader)
        throws JAXBException
    {
        final JAXBContext jctx = getJAXBContext(javaType);
        final Unmarshaller unm = jctx.createUnmarshaller();
        return unm.unmarshal(reader);
    }

    /**
     * Convert source data to JAXB data type specified
     * by the Class reference instance.
     *
     * @param clazz Conversion target type as a Class.
     * @param string A string for input source.
     * @return Converted type
     * @throws JAXBException base type for JAXB exceptions, many possible reasons
     */
    public static Object unmarshal(final Class<? extends Object> clazz, final String string)
        throws JAXBException
    {
        return unmarshal(clazz, new StringReader(string));
    }
}
