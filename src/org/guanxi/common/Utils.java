//: "The contents of this file are subject to the Mozilla Public License
//: Version 1.1 (the "License"); you may not use this file except in
//: compliance with the License. You may obtain a copy of the License at
//: http://www.mozilla.org/MPL/
//:
//: Software distributed under the License is distributed on an "AS IS"
//: basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//: License for the specific language governing rights and limitations
//: under the License.
//:
//: The Original Code is Guanxi (http://www.guanxi.uhi.ac.uk).
//:
//: The Initial Developer of the Original Code is Alistair Young alistair@codebrane.com
//: All Rights Reserved.
//:

package org.guanxi.common;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.apache.xml.security.utils.Base64;
import org.apache.xml.security.exceptions.Base64DecodingException;
import org.apache.xmlbeans.XmlObject;
import org.guanxi.xal.saml_2_0.metadata.EntitiesDescriptorDocument;

import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import java.util.*;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.rmi.server.UID;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;

/**
 * <font size=5><b></b></font>
 *
 * @author Alistair Young alistair@smo.uhi.ac.uk
 */
public class Utils {
  /** OS dependent line engine, e.g. \n */
  public static final String LINE_ENDING = System.getProperty("line.separator");
  
  /**
   * Returns the parameters and their values from an HTTP request
   *
   * @param request HTTP request object
   * @return Hashtable of parameters and their values
   */
  public static Hashtable<String, String> getRequestParameters(HttpServletRequest request) {
    Hashtable<String, String> params = new Hashtable<String, String>();
    Enumeration<?> e = request.getParameterNames();
    String name,value;

    while (e.hasMoreElements()) {
      name = (String)e.nextElement();
      value = request.getParameter(name);
      params.put(name, value);
    }

    return params;
  }

  public static String getUniqueID() {
    UID uid = new UID();
    return uid.toString();
  }

  public static String base64(Document inDocToEncode) {
    try {
      DOMSource domSource = new DOMSource(inDocToEncode);
      StringWriter writer = new StringWriter();
      StreamResult result = new StreamResult(writer);
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer transformer = tf.newTransformer();
      transformer.transform(domSource, result);
      return Base64.encode(writer.toString().getBytes());
    }
    catch(TransformerException te) {
      return null;
    }
  }

  public static String decodeBase64(String b64Data) {
    try {
      return new String(Base64.decode(b64Data));
    }
    catch(Base64DecodingException bde) {
      return null;
    }
  }

  /**
   * Gets the text value of a particular node in the config file
   *
   * @param configNode The parent node of the node we're interested in
   * @param option The node whose text we want
   * @return Text value of the node
   */
  public static String getConfigOption(Node configNode, String option) {
    NodeList childNodes = null;
    int childCount = 0;

    NodeList nodes = configNode.getChildNodes();

    if (nodes == null) return "-1";

    for (int count=0; count < nodes.getLength(); count++) {
      if (nodes.item(count).getLocalName() == null) continue;

      // Have we found the node we're looking for?
      if (nodes.item(count).getLocalName().equalsIgnoreCase(option)) {
        // Get the child nodes. We're looking for the text node containing the value
        childNodes = nodes.item(count).getChildNodes();

        for (childCount=0; childCount < childNodes.getLength(); childCount++) {
          // Got a text node?
          if (childNodes.item(childCount).getNodeType() == Node.TEXT_NODE) {
            // Anything in it?
            if (childNodes.item(childCount).getNodeValue() != null)
              // Sometimes you can get blank text nodes with Xerces
              if (!childNodes.item(childCount).getNodeValue().equalsIgnoreCase("")) {
                return childNodes.item(childCount).getNodeValue();
            }
          }
        }
      }
    }

    return "-1";
  }

  public static void zipDirectory(String dir, ZipOutputStream zipStream) {
    try {
      File dirToZip = new File(dir);

      // Have a gander in the directory to see what's there
      String[] dirList = dirToZip.list();

      byte[] readBuffer = new byte[2156];
      int bytesIn = 0;

      for (int count=0; count < dirList.length; count++) {
        File dirFile = new File(dirToZip, dirList[count]);

        // If the next entity is a directory, call recursively
        if (dirFile.isDirectory()) {
          zipDirectory(dirFile.getPath(), zipStream);
          continue;
        }

        // Get a handle to the file...
        FileInputStream fis = new FileInputStream(dirFile);
        // ...get an entry ready for it ...
        ZipEntry zipEntry = new ZipEntry(dirFile.getPath());
        // ...bung it in the ZIP stream...
        zipStream.putNextEntry(zipEntry);
        // ...and read the data into the ZIP stream
        while ((bytesIn = fis.read(readBuffer)) != -1) {
          zipStream.write(readBuffer, 0, bytesIn);
        }
        
        fis.close();
      }
    }
    catch(Exception e) {
      System.out.println(e);
    }
  }

  /**
   * Creates an ID that is compatible with XML Schema
   *
   * @return ID that is compatible with XML Schema
   */
  public static String createNCNameID() {
    // xsd:NCName is derived from xsd:Name, which can't start with a number
    String id = new UID().toString();
    id = id.replaceAll(":", "-");
    id = "GUANXI-" + id;
    return id;
  }

  /**
   * Parses a SAML2 Metadata document
   *
   * @param metadataURL The url of the metadata
   * @return EntitiesDescriptorDocument for the metadata
   * @param who The value of the User-Agent header to set
   * @throws GuanxiException if an error occurs
   */
  public static EntitiesDescriptorDocument parseSAML2Metadata(String metadataURL, String who) throws GuanxiException {
    try {
      HttpURLConnection httpURL = (HttpURLConnection)new URL(metadataURL).openConnection();
      httpURL.setRequestProperty("User-Agent", who);
      InputStream in = httpURL.getInputStream();
      BufferedReader buffer = new BufferedReader(new InputStreamReader(in));
      StringBuffer stringBuffer = new StringBuffer();
      String line = null;
      while ((line = buffer.readLine()) != null) {
        stringBuffer.append(line);
      }
      in.close();

      return EntitiesDescriptorDocument.Factory.parse(stringBuffer.toString());
    }
    catch(Exception e) {
      throw new GuanxiException(e);
    }
  }

  /**
   * Converts a local time to UTC Zulu format:
   * 2005-06-21T11:13:29Z
   *
   * @param obj The XmlObject that contains the local time in an attribute
   * @param interval the interval in minutes to add to any other time attrbutes
   */
  public static void zuluXmlObject(XmlObject obj, int interval) {
    SimpleDateFormat zulu = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    zulu.setTimeZone(TimeZone.getTimeZone("GMT"));
    Calendar calNow = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    Calendar calAfter = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    calAfter.add(Calendar.MINUTE, interval);

    NamedNodeMap attrs = obj.getDomNode().getAttributes();
    for (int cc=0; cc < attrs.getLength(); cc++) {
      Node attr = attrs.item(cc);
      if (attr.getNodeName().equals("NotBefore")) {
        attr.setNodeValue(zulu.format(calNow.getTime()));
      }
      if (attr.getNodeName().equals("NotOnOrAfter")) {
        attr.setNodeValue(zulu.format(calAfter.getTime()));
      }
      if (attr.getNodeName().equals("IssueInstant")) {
        attr.setNodeValue(zulu.format(calNow.getTime()));
      }
      if (attr.getNodeName().equals("AuthenticationInstant")) {
        attr.setNodeValue(zulu.format(calNow.getTime()));
      }
    }
  }

  /**
   * Generates local time in UTC Zulu format:
   * 2005-06-21T11:13:29Z
   *
   * @return The time now in Zulu format
   */
  public static String zuluNow() {
    SimpleDateFormat zulu = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    zulu.setTimeZone(TimeZone.getTimeZone("GMT"));
    Calendar calNow = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    return zulu.format(calNow.getTime());
  }
  
  /**
   * This reads the input stream completely, returning the result. The input stream is
   * always in a closed state after calling this, even if an error has occurred.
   * 
   * @param in
   * @return
   * @throws IOException
   */
  public static byte[] read(InputStream in) throws IOException {
    ByteArrayOutputStream out;
    byte[] buffer;
    int read;
    
    out = new ByteArrayOutputStream();
    buffer = new byte[1024];
    
    try {
      while ( (read = in.read(buffer)) != -1 ) {
        out.write(buffer, 0, read);
      }
      
      return out.toByteArray(); // ByteArrayInputStream.close() has no effect - see the JavaDoc!
    }
    finally {
      in.close();
    }
  }
}
