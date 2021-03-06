/*
 * The MIT License
 *
 * Copyright 2017 FactsMission AG, Switzerland.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.factsmission.tlds;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.clerezza.commons.rdf.BlankNode;
import org.apache.clerezza.commons.rdf.BlankNodeOrIRI;
import org.apache.clerezza.commons.rdf.Graph;
import org.apache.clerezza.commons.rdf.IRI;
import org.apache.clerezza.commons.rdf.Literal;
import org.apache.clerezza.commons.rdf.RDFTerm;
import org.apache.clerezza.commons.rdf.Triple;
import org.apache.clerezza.rdf.core.serializedform.SerializingProvider;
import org.apache.clerezza.rdf.core.serializedform.SupportedFormat;
import org.apache.clerezza.rdf.core.serializedform.UnsupportedSerializationFormatException;

@SupportedFormat("text/html")
public class RDFaSerializer implements SerializingProvider {

    @Override
    public void serialize(OutputStream outputStream, Graph graph, String formatIdentifier) {
        if (!formatIdentifier.equals("text/html")) {
            throw new UnsupportedSerializationFormatException("text/html");
        }
        PrintWriter out;
        try {
            out = new PrintWriter(new OutputStreamWriter(outputStream, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        Set<BlankNodeOrIRI> subjects = new HashSet<>();
        Set<RDFTerm> objects = new HashSet<>();
        for (Triple t : graph) {
            subjects.add(t.getSubject());
            RDFTerm object = t.getObject();
            objects.add(object);

        }
        Set<BlankNodeOrIRI> exclusiveSubjects = new HashSet<>();
        exclusiveSubjects.addAll(subjects);
        exclusiveSubjects.removeAll(objects);
        Set<BlankNodeOrIRI> otherSubjects = new HashSet<>();
        otherSubjects.addAll(subjects);
        otherSubjects.removeAll(exclusiveSubjects);
        Map<BlankNode, String> bNodeLabels = new HashMap<>();
        out.println("<table>");
        printResources(exclusiveSubjects, graph, bNodeLabels, out);
        printResources(otherSubjects, graph, bNodeLabels, out);
        out.println("</table>");
        out.flush();
    }

    private void printResources(Collection<BlankNodeOrIRI> nodes, Graph graph, Map<BlankNode, String> bNodeLabels, PrintWriter out) {
        for (BlankNodeOrIRI node : nodes) {
            out.println("    <tbody about='" + getLabel(node, bNodeLabels) + "' >");
            out.println("        <tr><td colspan='2'><h1>" + getLabel(node, bNodeLabels) + "</h1></td></td>");
            printProperties(node, graph, bNodeLabels, out);
            out.println("    </tbody>");
        }

    }

    private void printProperties(BlankNodeOrIRI node, Graph graph, Map<BlankNode, String> bNodeLabels, PrintWriter out) {
        Iterator<Triple> matches = graph.filter(node, null, null);
        Map<IRI, Set<RDFTerm>> propertyValuesMap = new HashMap<>();
        while (matches.hasNext()) {
            Triple t = matches.next();
            Set<RDFTerm> values = propertyValuesMap.containsKey(t.getPredicate())
                    ? propertyValuesMap.get(t.getPredicate())
                    : ((Supplier<Set<RDFTerm>>) () -> {
                        Set<RDFTerm> n = new HashSet<>();
                        propertyValuesMap.put(t.getPredicate(), n);
                        return n;
                    }).get();
            values.add(t.getObject());
        }

        for (final Map.Entry<IRI, Set<RDFTerm>> e : propertyValuesMap.entrySet()) {
            out.println("        <tr>");
            out.println("            <td rowspan='"+e.getValue().size()+"'>");
            out.println("                <a href='" + getLabel(e.getKey(), bNodeLabels) + "'>" + getLabel(e.getKey(), bNodeLabels) + "</a>");
            out.println("            </td>");
            out.println("            <td>");
            out.println(String.join("</td></tr><tr><td>", e.getValue().stream().map(object -> {
                if (object instanceof BlankNodeOrIRI) {
                    return "                <a property='" + getLabel(e.getKey(), bNodeLabels) + "' resource='" + getLabel((BlankNodeOrIRI) object, bNodeLabels)
                            + (object instanceof IRI ? "' href='" + getLabel((BlankNodeOrIRI) object, bNodeLabels) : "")
                            + "'>" + getLabel((BlankNodeOrIRI) object, bNodeLabels) + "</a>";
                } else {
                    return "                <span datatype='" + ((Literal) object).getDataType().getUnicodeString() + "' property='" + getLabel(e.getKey(), bNodeLabels) + "'>" + ((Literal) object).getLexicalForm() + "</span>";
                }

            }).collect(Collectors.toList())));
            out.println("            </td>");
            out.println("        </tr>");
            out.flush();
        }

    }

    public static Function<List<String>, String> joiningLastDelimiter(
            String delimiter, String lastDelimiter) {
        return list -> {
                    int last = list.size() - 1;
                    //if (last < 1) return String.join(delimiter, list);
                    return String.join(lastDelimiter,
                        String.join(delimiter, list.subList(0, last)),
                        list.get(last));
                };
    }
    
    private String getLabel(BlankNodeOrIRI node, Map<BlankNode, String> bNodeLabels) {
        return node instanceof BlankNode ? getLabel((BlankNode) node, bNodeLabels) : ((IRI) node).getUnicodeString();
    }

    private String getLabel(BlankNode node, Map<BlankNode, String> bNodeLabels) {
        if (!bNodeLabels.containsKey(node)) {
            bNodeLabels.put(node, "_:" + bNodeLabels.size());
        }
        return bNodeLabels.get(node);
    }

}
