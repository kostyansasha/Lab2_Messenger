package server.model;

import org.apache.log4j.Logger;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract class that describes work with thread for data exchange with the users
 *
 * @author Sasha Kostyan
 * @version %I%, %G%
 */
public class XmlMessage {
    private static Logger          LOG     = Logger.getLogger(XmlMessage.class);
    private static DocumentBuilder builder;

    /**
     * method create factory for work with XML.
     */
    private static  void paramLangXML() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            LOG.error("ParamLangXML err " + e);
        }
    }

    private static void writeChild(Element RootElement, Document doc, String strTeg, String str ) {
        Element NameElementTitle = doc.createElement(strTeg);
        NameElementTitle.appendChild(doc.createTextNode(str));
        RootElement.appendChild(NameElementTitle);
    }

    /**
     * Method for write of XML in a stream
     * @param xmlSet                parameters for send
     * @param out                   is a stream
     * @throws TransformerException if xml can not transform in out
     */
    public static void writeXMLinStream(XmlSet xmlSet, OutputStream out) throws TransformerException {
        paramLangXML();

        Document doc         = builder.newDocument();
        Element  RootElement = doc.createElement("XmlMessage");
        // id user
        writeChild(RootElement, doc, "IdUser", String.valueOf(xmlSet.getIdUser()));

        //dialogID
        if (xmlSet.getKeyDialog() != 0) {
            writeChild(RootElement, doc, "dialogID", String.valueOf(xmlSet.getKeyDialog()));
        }
        // general message
        if (xmlSet.getMessage() != null) {
            writeChild(RootElement, doc, "message", xmlSet.getMessage());

            Model.logMessage(xmlSet.getKeyDialog(), xmlSet.getMessage());                  //log message
        }

        //write name of active user
        if (xmlSet.getList() != null) {
            Element      elist;
            Integer      count = 1;
            List<String> list  = xmlSet.getList();

            for (String name : list) {
                elist = doc.createElement("list_user");
                RootElement.appendChild(elist);

                elist.setAttribute("id", count.toString());
                writeChild(elist, doc, "name", name);
                count++;
            }
        }

        // write else preference
        if (xmlSet.getPreference() != null) {
            writeChild(RootElement, doc, "else_preference", xmlSet.getPreference());
        }

        // add in XML
        doc.appendChild(RootElement);
        Transformer t =  TransformerFactory.newInstance().newTransformer();

        t.setOutputProperty(OutputKeys.METHOD, "xml");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        t.transform(new DOMSource(doc), new StreamResult(out));
    }


    private static String readChild(Document document, String strTeg) {
        NodeList nList = document.getElementsByTagName(strTeg);
        Node     node  = nList.item(0);
        return node.getTextContent();
    }

    /**
     * Method for read of XML from a stream
     * @param in            is a stream for read
     * @return              XmlSet
     * @throws IOException  if input stream can not be parse
     * @throws SAXException if input stream can not be parse
     */
    public static  XmlSet readXmlFromStream(InputStream in) throws IOException, SAXException {
        XmlSet xmlSet = new XmlSet(-1);

        paramLangXML();
        Document document;
        document = builder.parse(in);                                       //it will test in thread!!!
        document.getDocumentElement().normalize();

        // parsing id of user
        xmlSet.setIdUser(Integer.parseInt(readChild(document, "IdUser")));

        // parsing messageID and message
        try {
            int id = Integer.parseInt(readChild(document, "dialogID"));
            xmlSet.setKeyDialog(id);
            xmlSet.setMessage(readChild(document, "message"));
        } catch (Exception e){
            LOG.debug("messageID" + e);
        }

        // parsing list of user
        List<String> list  = new ArrayList<>();
        NodeList     nList = document.getElementsByTagName("list_user");

        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    list.add(eElement.getElementsByTagName("name").item(0).getTextContent());
                }
        }
        xmlSet.setList(list);

        // parsing else_preference
        try {
            xmlSet.setPreference(readChild(document, "else_preference"));
        } catch (Exception e){
            LOG.debug("else_preference"+e);
        }

        // if parsing was good return xmlSet else null
        if(xmlSet.getIdUser() != -1) {
            return xmlSet;
        } else {
            return null;
        }
    }
}
