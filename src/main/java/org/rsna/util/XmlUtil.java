/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Stack;

/**
 * Encapsulates static methods for working with XML objects.
 */
public class XmlUtil {

//	static final Logger logger = Logger.getLogger(XmlUtil.class);

	/**
	 * Get a DocumentBuilder that is namespace aware.
	 * @return a namespace-aware DocumentBuilder.
	 */
	public static DocumentBuilder getDocumentBuilder() throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		return dbf.newDocumentBuilder();
	}

	/**
	 * Parse an XML file.
	 * @param file the file containing the XML to parse.
	 * @return the XML DOM document.
	 */
	public static Document getDocument(File file) throws Exception {
		DocumentBuilder db = getDocumentBuilder();
		return db.parse(file);
	}

	/**
	 * Parse an XML string.
	 * @param xmlString the file containing the XML to parse.
	 * @return the XML DOM document.
	 */
	public static Document getDocument(String xmlString) throws Exception {
		StringReader sr = new StringReader(xmlString);
		DocumentBuilder db = getDocumentBuilder();
		return db.parse(new InputSource(sr));
	}

	/**
	 * Parse an XML InputStream.
	 * @param inputStream the stream to parse.
	 * @return the XML DOM document.
	 */
	public static Document getDocument(InputStream inputStream) throws Exception {
		DocumentBuilder db = getDocumentBuilder();
		return db.parse(new InputSource(inputStream));
	}

	/**
	 * Create a new empty XML DOM document.
	 * @return the XML DOM document.
	 */
	public static Document getDocument() throws Exception {
		DocumentBuilder db = getDocumentBuilder();
		return db.newDocument();
	}

	/**
	 * Parse an XML file if it exists or a resource if the file does not exist.
	 * @param file the file containing the XML to parse.
	 * @param resource the resource containing the XML to parse if the file does not exist.
	 * @return the XML DOM document.
	 */
	public static Document getDocument(File file, String resource) throws Exception {
		InputStream in = FileUtil.getStream(file, resource);
		return getDocument(in);
	}

	/**
	 * Transform an XML file using an XSL file and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param doc the document to transform.
	 * @param xsl the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed text.
	 */
	public static String getTransformedText(File doc, File xsl, Object[] params) throws Exception {
		return getTransformedText(new StreamSource(doc), new StreamSource(xsl), params);
	}

	/**
	 * Transform an XML DOM Document using an XSL file and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param doc the document to transform.
	 * @param xsl the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed text.
	 */
	public static String getTransformedText(Document doc, File xsl, Object[] params) throws Exception {
		return getTransformedText(new DOMSource(doc), new StreamSource(xsl), params);
	}

	/**
	 * Transform an XML document using an XSL DOM document and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param doc the document to transform.
	 * @param xsl the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed text.
	 */
	public static String getTransformedText(Document doc, Document xsl, Object[] params) throws Exception {
		return getTransformedText(new DOMSource(doc), new DOMSource(xsl), params);
	}

	/**
	 * General method for transformation to text. Transform a Source
	 * document using a Source XSL document and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param doc the document to transform.
	 * @param xsl the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed text.
	 */
	public static String getTransformedText(Source doc, Source xsl, Object[] params) throws Exception {
		TransformerFactory tFactory = TransformerFactory.newInstance();
		Transformer transformer = tFactory.newTransformer(xsl);
		if ((params != null) && (params.length > 1)) {
			for (int i=0; i<params.length; i=i+2) {
				transformer.setParameter((String)params[i], params[i+1]);
			}
		}
		StringWriter sw = new StringWriter();
		transformer.transform(doc, new StreamResult(sw));
		return sw.toString();
	}

	/**
	 * Transform an XML file using an XSL file and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param doc the document to transform.
	 * @param xsl the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed DOM Document.
	 */
	public static Document getTransformedDocument(File doc, File xsl, Object[] params) throws Exception {
		return getTransformedDocument(new StreamSource(doc), new StreamSource(xsl), params);
	}

	/**
	 * Transform an XML DOM Document using an XSL file and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param doc the document to transform.
	 * @param xsl the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed DOM Document.
	 */
	public static Document getTransformedDocument(Document doc, File xsl, Object[] params) throws Exception {
		return getTransformedDocument(new DOMSource(doc), new StreamSource(xsl), params);
	}

	/**
	 * Transform an XML file using an XSL DOM Document and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param doc the file containing the XML to transform.
	 * @param xsl the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed DOM Document.
	 */
	public static Document getTransformedDocument(File doc, Document xsl, Object[] params) throws Exception {
		return getTransformedDocument(new StreamSource(doc), new DOMSource(xsl), params);
	}

	/**
	 * Transform an XML document using an XSL DOM document and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param doc the document to transform.
	 * @param xsl the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed DOM Document.
	 */
	public static Document getTransformedDocument(Document doc, Document xsl, Object[] params) throws Exception {
		return getTransformedDocument(new DOMSource(doc), new DOMSource(xsl), params);
	}

	/**
	 * General method for transformation to a DOM Document. Transform a Source
	 * document using a Source XSL document and an array of parameters.
	 * The parameter array consists of a sequence of pairs of (String parametername)
	 * followed by (Object parametervalue) in an Object[].
	 * @param doc the XML document to transform.
	 * @param xsl the XSL transformation program.
	 * @param params the array of transformation parameters.
	 * @return the transformed text.
	 */
	public static Document getTransformedDocument(Source doc, Source xsl, Object[] params) throws Exception {
		TransformerFactory tFactory = TransformerFactory.newInstance();
		Transformer transformer = tFactory.newTransformer(xsl);
		if ((params != null) && (params.length > 1)) {
			for (int i=0; i<params.length; i=i+2) {
				transformer.setParameter((String)params[i], params[i+1]);
			}
		}
		DOMResult domResult = new DOMResult();
		transformer.transform(doc, domResult);
		return (Document) domResult.getNode();
	}

	/**
	 * Determine whether an element has any child elements.
	 * @param element the element to check.
	 * @return true if the element has child elements; false otherwise.
	 */
	public static boolean hasChildElements(Element element) {
		Node child = element.getFirstChild();
		while (child != null) {
			if (child.getNodeType() == Node.ELEMENT_NODE) return true;
			child = child.getNextSibling();
		}
		return false;
	}

	/**
	 * Get the first child element with a specified name.
	 * If the starting node is a Document, use the document element
	 * as the starting point. Only first-generation children of the
	 * starting node are searched.
	 * @param node the starting node.
	 * @param name the name of the child to find.
	 * @return the first child element with the specified name, or null
	 * if the starting node is null or if no child with the name exists.
	 */
	public static Element getFirstNamedChild(Node node, String name) {
		if (node == null) return null;
		if (node instanceof Document) node = ((Document)node).getDocumentElement();
		if ( !(node instanceof Element) ) return null;
		Node child = node.getFirstChild();
		while (child != null) {
			if ((child instanceof Element) && child.getNodeName().equals(name)) {
				return (Element)child;
			}
			child = child.getNextSibling();
		}
		return null;
	}

	/**
	 * Get an element specified by a starting node and a path string.
	 * If the starting node is a Document, use the document element
	 * as the starting point.
	 * <ul><li>A path to an element has the form: elem1/.../elemN</ul>
	 * @param node the top of the tree to search.
	 * @param path the path from the top of the tree to the desired element.
	 * @return the first element matching the path, or null if no element
	 * exists at the path location or if the starting node is not an element.
	 */
	public static Element getElementViaPath(Node node, String path) {
		if (node instanceof Document) node = ((Document)node).getDocumentElement();
		if (!(node instanceof Element)) return null;
		int k = path.indexOf("/");
		String firstPathElement = path;
		if (k > 0) firstPathElement = path.substring(0,k);
		if (node.getNodeName().equals(firstPathElement)) {
			if (k < 0) return (Element)node;
			path = path.substring(k+1);
			NodeList nodeList = ((Element)node).getChildNodes();
			Node n;
			for (int i=0; i<nodeList.getLength(); i++) {
				n = nodeList.item(i);
				if ((n instanceof Element) && ((n = getElementViaPath(n,path)) != null))
					return (Element)n;
			}
		}
		return null;
	}

	/**
	 * Get the value of a node specified by a starting node and a
	 * path string. If the starting node is a Document, use the
	 * document element as the starting point.
	 * <ul>
	 * <li>A path to an element has the form: elem1/.../elemN
	 * <li>A path to an attribute has the form: elem1/.../elemN@attr
	 * </ul>
	 * The value of an element node is the sum of all the element's
	 * first generation child text nodes. Note that this is not what you
	 * would get from a mixed element in an XSL program.
	 * @param node the top of the tree to search.
	 * @param path the path from the top of the tree to the desired node.
	 * @return the value of the first node matching the path, or the
	 * empty string if no node exists at the path location or if the
	 * starting node is not an element.
	 */
	public static String getValueViaPath(Node node, String path) {
		if (node instanceof Document) node = ((Document)node).getDocumentElement();
		if (!(node instanceof Element)) return "";
		path = path.trim();
		int kAtsign = path.indexOf("@");

		//If the target is an element, get the element's value.
		if (kAtsign == -1) {
			Element target = getElementViaPath(node,path);
			if (target == null) return "";
			return target.getTextContent();
		}

		//The target is an attribute; first find the element.
		String subpath = path.substring(0,kAtsign);
		Element target = getElementViaPath(node,subpath);
		if (target == null) return null;
		String name = path.substring(kAtsign+1);
		return target.getAttribute(name);
	}

	/**
	 * Make a String from an XML DOM Node.
	 * @param node the node at the top of the tree.
	 * @return the XML string for the node and its children.
	 * If the node is a DOCUMENT_NODE, the string includes
	 * an XML declaration specifying an encoding of UTF-8.
	 */
	public static String toString(Node node) {
		StringBuffer sb = new StringBuffer();
		renderNode(sb, node);
		return sb.toString();
	}

	//Recursively walk the tree and write the nodes to a StringWriter.
	private static void renderNode(StringBuffer sb, Node node) {
		if (node == null) { sb.append("null"); return; }
		switch (node.getNodeType()) {

			case Node.DOCUMENT_NODE:
				sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
				Node root = ((Document)node).getDocumentElement();
				renderNode(sb, root);
				break;

			case Node.ELEMENT_NODE:
				String name = getNodeNameWithNamespace(node);
				NamedNodeMap attributes = node.getAttributes();
				if (attributes.getLength() == 0) {
					sb.append("<" + name + ">");
				}
				else {
					sb.append("<" + name + " ");
					int attrlen = attributes.getLength();
					for (int i=0; i<attrlen; i++) {
						Node attr = attributes.item(i);
						String attrName = getNodeNameWithNamespace(attr);
						sb.append(attrName + "=\"" + escapeChars(attr.getNodeValue()));
						if (i < attrlen-1)
							sb.append("\" ");
						else
							sb.append("\">");
					}
				}
				NodeList children = node.getChildNodes();
				if (children != null) {
					for (int i=0; i<children.getLength(); i++) {
						renderNode(sb,children.item(i));
					}
				}
				sb.append("</" + name + ">");
				break;

			case Node.TEXT_NODE:
				sb.append(escapeChars(node.getNodeValue()));
				break;

			case Node.CDATA_SECTION_NODE:
				sb.append("<![CDATA[" + node.getNodeValue() + "]]>");
				break;

			case Node.PROCESSING_INSTRUCTION_NODE:
				sb.append("<?" + node.getNodeName() + " " +
					escapeChars(node.getNodeValue()) + "?>");
				break;

			case Node.ENTITY_REFERENCE_NODE:
				sb.append("&" + node.getNodeName() + ";");
				break;

			case Node.DOCUMENT_TYPE_NODE:
				// Ignore document type nodes
				break;

			case Node.COMMENT_NODE:
				sb.append("<!--" + node.getNodeValue() + "-->");
				break;
		}
		return;
	}

	/**
	 * Make a pretty String from an XML DOM Node. Note: this method
	 * inserts leading and trailing whitespace in text nodes. Thus,
	 * the result of this method may be functionally different from
	 * the original XML. It should be used when such whitespace is
	 * not important or when the objective is just to make a string
	 * for printing.
	 * @param node the node at the top of the tree.
	 * @return the XML string for the node and its children.
	 */
	public static String toPrettyString(Node node) {
		StringBuffer sb = new StringBuffer();
		renderNode(sb, node, "", "    ", "<", ">", "\n");
		return sb.toString();
	}

	/**
	 * Make a pretty String from an XML DOM Node for display as HTML.
	 * @param node the node at the top of the tree.
	 * @return the XML string for the node and its children.
	 */
	public static String toPrettyHTMLString(Node node) {
		StringBuffer sb = new StringBuffer();
		renderNode(sb, node, "", "&nbsp;&nbsp;&nbsp;&nbsp;", "&lt;", "&gt;", "<br>");
		return sb.toString();
	}

	//Recursively walk the tree and write the nodes to a StringWriter.
	private static void renderNode(StringBuffer sb,
									Node node,
									String margin,
									String indent,
									String lab,
									String rab,
									String nl) {
		if (node == null) { sb.append("null"); return; }
		switch (node.getNodeType()) {

			case Node.DOCUMENT_NODE:
				//sb.append(margin + lab +"?xml version=\"1.0\" encoding=\"UTF-8\"?" + rab + nl);
				Node root = ((Document)node).getDocumentElement();
				renderNode(sb, root, margin, indent, lab, rab, nl);
				break;

			case Node.ELEMENT_NODE:
				String name = getNodeNameWithNamespace(node);
				NodeList children = node.getChildNodes();
				int nChildren = children.getLength();
				NamedNodeMap attributes = node.getAttributes();
				int nAttrs = attributes.getLength();

				boolean singleShortTextChild = (nAttrs == 0) && (nChildren == 1)
											&& (children.item(0).getNodeType() == Node.TEXT_NODE)
											&& (children.item(0).getTextContent().length() < 70)
											&& (!children.item(0).getTextContent().contains("\n"));

				if (singleShortTextChild) {
					sb.append(margin + lab + name + ((nChildren == 0) ? "/" : "") + rab);
				}
				else if (nAttrs == 0 && !singleShortTextChild) {
					sb.append(margin + lab + name + ((nChildren == 0) ? "/" : "") + rab + nl);
				}
				else if (nAttrs == 1) {
					Node attr = attributes.item(0);
					String attrName = getNodeNameWithNamespace(attr);
					sb.append(margin + lab + name +  " "
								+ attrName + "=\"" + escapeChars(attr.getNodeValue()) + "\""
								+ ((nChildren == 0) ? "/" : "")
								+ rab + nl);
				}
				else {
					sb.append(margin + lab + name + nl);
					for (int i=0; i<nAttrs; i++) {
						Node attr = attributes.item(i);
						String attrName = getNodeNameWithNamespace(attr);
						sb.append(margin + indent + attrName + "=\"" + escapeChars(attr.getNodeValue()));
						if (i < nAttrs - 1)
							sb.append("\"" + nl);
						else
							sb.append("\"" + ((nChildren == 0) ? "/" : "") + rab + nl);
					}
				}
				if (singleShortTextChild) {
					String text = escapeChars(node.getTextContent());
					sb.append(text.trim());
					sb.append(lab + "/" + name + rab + nl);				}
				else {
					for (int i=0; i<nChildren; i++) {
						renderNode(sb, children.item(i), margin+indent, indent, lab, rab, nl);
					}
				}
				if (nChildren != 0 && !singleShortTextChild) sb.append(margin + lab + "/" + name + rab + nl);
				break;

			case Node.TEXT_NODE:
				String text = escapeChars(node.getNodeValue());
				String[] lines = text.split("\n");
				for (String line : lines) {
					line = line.trim();
					if (!line.equals("")) sb.append(margin + line + nl);
				}
				break;

			case Node.CDATA_SECTION_NODE:
				String cdataText = node.getNodeValue();
				String[] cdataLines = cdataText.split("\n");
				sb.append(margin + lab + "![CDATA[" + nl);
				for (String line : cdataLines) {
					line = line.trim();
					if (!line.equals("")) sb.append(margin + indent + line + nl);
				}
				sb.append(margin + "]]" + rab + nl);
				break;

			case Node.PROCESSING_INSTRUCTION_NODE:
				sb.append(margin + lab + "?" + node.getNodeName() + " " +
					escapeChars(node.getNodeValue()) + "?" + rab + nl);
				break;

			case Node.ENTITY_REFERENCE_NODE:
				sb.append("&" + node.getNodeName() + ";");
				break;

			case Node.DOCUMENT_TYPE_NODE:
				// Ignore document type nodes
				break;

			case Node.COMMENT_NODE:
				sb.append(margin + lab + "!--" + node.getNodeValue() + "--" + rab + nl);
				break;
		}
		return;
	}

	private static String getNodeNameWithNamespace(Node node) {
		String name = node.getNodeName();
		String ns = node.getNamespaceURI();
		String prefix = (ns != null) ? node.lookupPrefix(ns) : null;
		if ((prefix != null) && !name.startsWith(prefix+":")) {
			name = prefix + ":" + name;
		}
		return name;
	}

	/**
	 * Escape the ampersand, less-than, greater-than, single and double quote
	 * characters in a string, replacing with their XML entities.
	 * @param theString the string to escape.
	 * @return the modified string.
	 */
	public static String escapeChars(String theString) {
		return theString.replace("&","&amp;")
						.replace(">","&gt;")
						.replace("<","&lt;")
						.replace("\"","&quot;")
						.replace("'","&apos;");
	}

	/**
	 * Evaluate a boolean script for a Document. See the RSNA
	 * CTP wiki article (The CTP XmlFilter) for information on the
	 * script language.
	 * @param document the document
	 * @param script the expression to compute based on the values
	 * in the XML Document.
	 * @return the computed boolean value of the script.
	 */
	public static boolean matches(Document document, String script) {
		Element root = document.getDocumentElement();
		Tokenizer tokenizer = new Tokenizer(script,root);
		Stack<Operator> operators = new Stack<Operator>();
		Stack<Token> tokens = new Stack<Token>();
		operators.push(Operator.createSentinel());

		//Get the expression, evaluate it, and return the result.
		try {
			expression(tokenizer, operators, tokens);
			tokenizer.expect(Token.END);
			return unstack(tokens);
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		return false;
	}

	static boolean unstack(Stack<Token> tokens) {
		if (tokens.size() == 0) return false;
		Token tok = tokens.pop();
		if (tok instanceof Operand)
			return ((Operand)tok).getValue();
		else {
			Operator op = (Operator)tok;
			boolean value = false;
			boolean v1, v2;
			if (op.c == '!')
				value = !unstack(tokens);
			else if (op.c == '+') {
				//note: you must unstack separately and then do the logic
				//or the optimizer may omit one unstack, leaving the stack
				//in a mess.
				v1 = unstack(tokens);
				v2 = unstack(tokens);
				value = v1 || v2;
			}
			else if (op.c == '*') {
				//see the note above.
				v1 = unstack(tokens);
				v2 = unstack(tokens);
				value = v1 && v2;
			}
			return value;
		}
	}

	static void expression(Tokenizer t, Stack<Operator> ops, Stack<Token> toks) throws Exception {
		parse(t,ops,toks);
		while (t.next().isOperator() && ((Operator)t.next()).isBinary()) {
			pushOperator(t.next(), ops, toks);
			t.consume();
			parse(t, ops, toks);
		}
		while (!ops.peek().isSentinel()) {
			popOperator(ops, toks);
		}
	}

	static void parse(Tokenizer t, Stack<Operator> ops, Stack<Token> toks) throws Exception {
		if (t.next().isOperand()) {
			toks.push(t.next());
			t.consume();
		}
		else if (t.next().isLP()) {
			t.consume();
			ops.push(Operator.createSentinel());
			expression(t, ops, toks);
			t.expect(Token.RP);
			ops.pop();
		}
		else if (t.next().isOperator() && ((Operator)t.next()).isUnary()) {
			pushOperator(t.next(), ops, toks);
			t.consume();
			parse(t, ops,  toks);
		}
		else throw new Exception("Failure in parsing the script.");
	}

	static void popOperator(Stack<Operator> ops, Stack<Token> toks) {
		toks.push(ops.pop());
	}

	static void pushOperator(Token tok, Stack<Operator> ops, Stack<Token> toks) {
		Operator op = (Operator)tok;
		while (ops.peek().isHigherThan(op))
			popOperator(ops, toks);
		ops.push(op);
	}

	//The rest of the code is for parsing the script and evaluating the result.
	static class Tokenizer {
		String script;
		int index;
		Token nextToken;
		Element root;

		public Tokenizer(String script, Element root) {
			this.script = script;
			this.root = root;
			index = 0;
			nextToken = getToken();
		}
		public void expect(int type) throws Exception {
			if (nextToken.equals(type))
				consume();
			else
				throw new Exception(
					"Error in script: "
					+Token.getTypeName(type)
					+" expected, but "
					+Token.getTypeName(nextToken.getType())
					+" found.");
		}
		public Token next() {
			return nextToken;
		}
		public Token consume() {
			Token temp = nextToken;
			nextToken = getToken();
			return temp;
		}
		Token getToken() {
			skipWhitespace();
			if (index >= script.length())
				return new End();
			char c = script.charAt(index);
			if ((c == '"') || (c == '/') || Character.isLetter(c))
				return new Operand(this, root);
			else if (c == '(')
				return new LP(this);
			else if (c == ')')
				return new RP(this);
			else if (Operator.isOperator(c))
				return new Operator(this);
			return new Unknown();
		}
		void skipWhitespace() {
			while ((index < script.length()) && Character.isWhitespace(script.charAt(index)))
				index++;
		}
		public char getChar() {
			if (index < script.length())
				return script.charAt(index++);
			return 0;
		}
	}

	static class Operator extends Token {
		public char c;	//the operator character
		public int p;	//the precedence
		static String ops = "?+*!";
		static int[] prec = {0,1,2,3};

		public Operator(Tokenizer t) {
			super(OPERATOR);
			this.c = t.getChar();
			this.p = ops.indexOf(c);
			if (p != -1) p = prec[p];
		}
		public Operator(char c) {
			super(OPERATOR);
			this.c = c;
			this.p = ops.indexOf(c);
			if (p != -1) p = prec[p];
		}
		public static Operator createSentinel() {
			return new Operator('?');
		}
		public static boolean isOperator(char c) {
			int x = ops.indexOf(c);
			return (x > 0);
		}
		@Override
    public boolean isOperator() {
			return (p != -1);
		}
		public boolean isSentinel() {
			return (c == '?');
		}
		public boolean isUnary() {
			return (c == '!');
		}
		public boolean isBinary() {
			return (c == '+') || (c == '*');
		}
		public boolean isHigherThan(Operator q) {
			return (p >= q.p);
		}
		public boolean isLowerThan(Operator q) {
			return (p < q.p);
		}
	}

	static class Operand extends Token {
		public boolean value = false;;
		public Operand(Tokenizer t, Element root) {
			super(OPERAND);
			String identifier = getField(t,'.').trim();
			if ((identifier.length() > 1) &&
					identifier.startsWith("\"") &&
					identifier.endsWith("\"")) {
				identifier = identifier.substring(1, identifier.length()-1).trim();
			}
			if (identifier.equals("true"))
				value = true;
			else if (identifier.equals("false"))
				value = false;
			else {
				String method = getField(t,'(').trim();
				String match = getField(t,')').trim();
				if ((match.length() > 1) &&
						match.startsWith("\"") &&
						match.endsWith("\"")) {
					String element = getTextContent(root, identifier);
					String elementLC = element.toLowerCase();
					match = match.substring(1,match.length()-1);
					String matchLC = match.toLowerCase();
					if (method.equals("equals"))
						value = element.equals(match);
					else if (method.equals("equalsIgnoreCase"))
						value = element.equalsIgnoreCase(match);
					else if (method.equals("matches"))
						value = element.matches(match);
					else if (method.equals("contains"))
						value = (element.contains(match));
					else if (method.equals("containsIgnoreCase"))
						value = (elementLC.contains(matchLC));
					else if (method.equals("startsWith"))
						value = element.startsWith(match);
					else if (method.equals("startsWithIgnoreCase"))
						value = elementLC.startsWith(matchLC);
					else if (method.equals("endsWith"))
						value = element.endsWith(match);
					else if (method.equals("endsWithIgnoreCase"))
						value = elementLC.endsWith(matchLC);
				}
			}
		}
		String getField(Tokenizer t, char delim) {
			String f = "";
			char c;
			boolean inQuote = false;
			while ((c = t.getChar()) != 0) {
				if (c == '"') inQuote = !inQuote;
				if (!inQuote && (c == delim)) break;
				f += c;
			}
			return f;
		}
		public boolean getValue() {
			return value;
		}
	}

	static class LP extends Token {
		public LP(Tokenizer t) {
			super(LP);
			t.getChar();
		}
	}

	static class RP extends Token {
		public RP(Tokenizer t) {
			super(RP);
			t.getChar();
		}
	}

	static class End extends Token {
		public End() {
			super(END);
		}
	}

	static class Unknown extends Token {
		public Unknown() {
			super(UNKNOWN);
		}
	}

	static class Token {
		static int OPERATOR = 0;
		static int OPERAND = 1;
		static int LP = 2;
		static int RP = 3;
		static int END = -1;
		static int UNKNOWN = -2;
		int type;
		public Token(int type) {
			this.type = type;
		}
		public boolean equals(int type) {
			return (this.type == type);
		}
		public boolean isOperator() {
			return (type == OPERATOR);
		}
		public boolean isOperand() {
			return (type == OPERAND);
		}
		public boolean isLP() {
			return (type == LP);
		}
		public boolean isRP() {
			return (type == RP);
		}
		public boolean isEND() {
			return (type == END);
		}
		public int getType() {
			return type;
		}
		public String getTypeName() {
			return getTypeName(this.type);
		}
		public static String getTypeName(int type) {
			if (type == OPERATOR) return "OPERATOR";
			else if (type == OPERAND) return "OPERAND";
			else if (type == LP) return "LP";
			else if (type == RP) return "RP";
			else if (type == END) return "END";
			else return "UNKNOWN";
		}
	}

	/**
	 * Get the text content of an element identified by a path,
	 * where the path elements can include an index. The first
	 * path element must not have an index, and its name must
	 * match the name of the starting node. If the starting
	 * node is a Document, the root Element of the document is
	 * used as the starting point. Path elements must be separated
	 * by the slash character. If the path starts with a slash,
	 * the slash is ignored. If the element or attribute identified
	 * by the path is not present as a child of the starting node,
	 * the empty string is returned. If a path element identifies
	 * an attribute, any subsequent path elements are ignored.
	 * A path is in the form: /e1/e2/e3/... or /e1/e2/@attr
	 * Note the slash preceding the attribute's @-sign.
	 * @param node the starting node for the path. The first path
	 * element must match the name of this node.
	 * @param path the path to the target node.
	 * @return the full text value of the target node (including all
	 * descendent text nodes), or the empty string if the target is
	 * not a descendent of the starting node.
	 */
	public static String getTextContent(Node node, String path) {
		if (node instanceof Document) node = ((Document)node).getDocumentElement();
		if (!(node instanceof Element)) return "";
		Element el = (Element)node;
		path = path.replaceAll("\\s","");
		if (path.startsWith("/")) path = path.substring(1);
		String[] pathElements = path.split("/");
		if (!pathElements[0].equals(el.getTagName())) return "";
		for (int i=1; i<pathElements.length; i++) {
			String pe = pathElements[i];
			if (pe.startsWith("@")) {
				//If this path element identifies an attribute, return it
				//and ignore any further path elements.
				return el.getAttribute(pe.substring(1));
			}
			else {
				//This path element identifies an Element. It may have an index.
				//Get the index, if present, and get the element name.
				int n = 0;
				int k = pe.indexOf("[");
				int kk = pe.lastIndexOf("]");
				if ((k != -1) && (k < kk)) {
					try { n = Integer.parseInt(pe.substring(k+1, kk)); }
					catch (Exception ex) { return ""; }
					pe = pe.substring(0,k);
				}
				else if (k != kk) return "";
				//We now have the element name and the index.
				//Find the identified Element. We have to count
				//matching elements to find the one identified
				//by the index.
				int nn = 0;
				Node child = el.getFirstChild();
				while (child != null) {
					if ((child.getNodeType() == Node.ELEMENT_NODE)
							&& child.getNodeName().equals(pe)) {
						if (n == nn) break;
						nn++;
					}
					child = child.getNextSibling();
				}
				//If the child is null, we didn't find the identified Element.
				if (child == null) return "";
				//If we get here, we found it, now look for the next one.
				el = (Element)child;
			}
		}
		//Okay, we must be at the end of the path, and it must be an Element.
		//Return the text content of the element.
		return el.getTextContent();
	}

	/**
	 * Rename an element, replacing it in its document.
	 * @param element the element to rename.
	 * @param name the new element name.
	 * @return the renamed element.
	 */
	public static Element renameElement(Element element, String name) {
		if (element.getNodeName().equals(name)) return element;
		Element el = element.getOwnerDocument().createElement(name);

		//Copy the attributes
		NamedNodeMap attributes = element.getAttributes();
		int nAttrs = attributes.getLength();
		for (int i=0; i<nAttrs; i++) {
			Node attr = attributes.item(i);
			el.setAttribute(attr.getNodeName(), attr.getNodeValue());
		}

		//Copy the children
		Node node = element.getFirstChild();
		while (node != null) {
			Node clone = node.cloneNode(true);
			el.appendChild(clone);
			node = node.getNextSibling();
		}

		//Replace the element
		element.getParentNode().replaceChild(el, element);
		return element;
	}
}
