package at.infrasoft.apex.printserver;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

@WebServlet(urlPatterns = "/*", loadOnStartup = 1)
@Slf4j
public class ApexPrintServlet extends HttpServlet {
  @Data
  @Builder
  private static class RequestParameter {
    private String xml;
    private String template;
    private String type;
    private String format;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    try {
      final RequestParameter requestParameter = parseRequestBody(req);
      final String fopXml = transform(requestParameter);
      final FopFactory fopFactory = FopFactory.newInstance(new File(".").toURI());
      try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        final Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, outputStream);
        final Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new StreamSource(new StringReader(fopXml)), new SAXResult(fop.getDefaultHandler()));
        resp.setContentType("application/pdf");
        resp.setContentLength(outputStream.size());
        resp.getOutputStream().write(outputStream.toByteArray());
        resp.getOutputStream().flush();
      }
    } catch (Exception e) {
      log.error("Error while printing Document", e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private RequestParameter parseRequestBody(HttpServletRequest request) throws IOException {
    final String requestBody = IOUtils.toString(request.getInputStream());
    final RequestParameter.RequestParameterBuilder requestParameterBuilder = RequestParameter.builder();

    int indexOfXml = StringUtils.indexOf(requestBody, "xml=<?xml");
    if (indexOfXml > -1) {
      final String xml = requestBody.substring(indexOfXml + 4);
      if (xml.contains("</DOCUMENT>")) {
        requestParameterBuilder.xml(xml.substring(0, xml.indexOf("</DOCUMENT>") + "</DOCUMENT>".length()));
      } else if (xml.contains("</ROWSET>")) {
        requestParameterBuilder.xml(xml.substring(0, xml.indexOf("</ROWSET") + "</ROWSET>".length()));
      }
    }

    int indexOfTemplate = StringUtils.indexOf(requestBody, "template=<");
    if (indexOfTemplate > -1) {
      final String template = requestBody.substring(indexOfTemplate + "template=".length());
      if (template.contains("</xsl:stylesheet>")) {
        requestParameterBuilder.template(template.substring(0, template.indexOf("</xsl:stylesheet>") + "</xsl:stylesheet>".length()));
      }
    }

    int indexOfType = StringUtils.indexOf(requestBody, "_xtype=");
    if (indexOfType > -1) {
      final String type = requestBody.substring(indexOfType + "_xtype=".length());
      if (type.contains("&")) {
        requestParameterBuilder.type(type.substring(0, type.indexOf("&") - 1));
      } else {
        requestParameterBuilder.type(type);
      }
    }

    int indexOfFormat = StringUtils.indexOf(requestBody, "_xf=");
    if (indexOfFormat > -1) {
      final String format = requestBody.substring(indexOfFormat + "_xf=".length());
      if (format.contains("&")) {
        requestParameterBuilder.format(format.substring(0, format.indexOf("&") - 1));
      } else {
        requestParameterBuilder.format(format);
      }
    }

    return requestParameterBuilder.build();
  }

  private String transform(RequestParameter requestParameter) throws Exception {
    final DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    final Document xml = documentBuilder.parse(new InputSource(new StringReader(requestParameter.getXml())));
    final Transformer template = TransformerFactory.newInstance().newTransformer(new StreamSource(new StringReader(requestParameter.getTemplate())));
    try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      template.transform(new DOMSource(xml), new StreamResult(outputStream));
      return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
