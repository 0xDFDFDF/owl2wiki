package pl.df.owlToWiki.facade.article;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import net.sourceforge.jwbf.core.contentRep.SimpleArticle;
import pl.df.owlToWiki.facade.wiki.Template;

import java.util.Map;

/**
 * User: dominikfilipiak
 * Date: 17/03/2014
 * Time: 13:40
 */
public abstract class AbstractArticleBuilder {

    protected String rootRDFType;
    protected QueryExecution queryExecution;
    protected Template template;

    /**
     * Queries ontology ontModel via SPARQL query
     *
     * @param model       Ontology ontModel
     * @param queryString Query string
     * @return A result set
     */
    protected ResultSet queryModel(OntModel model, String queryString) {
        Query query = QueryFactory.create(queryString);
        queryExecution = QueryExecutionFactory.create(query, model);
        return queryExecution.execSelect();
    }

    /**
     * Article preparation for given resource and ontology.
     *
     * @param ontModel Ontology model with a reasoner
     * @param resource Resource
     * @param article  Article to prepare
     * @return Prepared article
     * @throws ArticleBuilderException
     */
    protected SimpleArticle prepareArticle(OntModel ontModel, RDFNode resource, SimpleArticle article) throws ArticleBuilderException {
        if (article.getTitle().isEmpty()) throw new ArticleBuilderException("Cannot create article with empty title");
        appendTemplateValues(article, ontModel, resource);
        if (!resource.toString().equals(rootRDFType)) {
            addCategoryFooter(article, resource, ontModel);
        }
        return article;
    }

    /**
     * Article preparation for given resource and ontology.
     *
     * @param ontModel Ontology model with a reasoner
     * @param model    Ontology model without a reasoner (for hierarchy sake)
     * @param resource Resource
     * @param article  Article to prepare
     * @return Prepared article
     * @throws ArticleBuilderException
     */
    protected SimpleArticle prepareArticle(OntModel ontModel, OntModel model, RDFNode resource, SimpleArticle article) throws ArticleBuilderException {
        if (article.getTitle().isEmpty()) throw new ArticleBuilderException("Cannot create article with empty title");
        appendTemplateValues(article, ontModel, resource);
        if (!resource.toString().equals(rootRDFType)) {
            addCategoryFooter(article, resource, model);
        }
        return article;
    }

    /**
     * Fills up the article with values corresponding to given template
     *
     * @param article  Article to prepare
     * @param model    Ontology model without a reasoner (for hierarchy sake)
     * @param resource Resource
     */
    protected void appendTemplateValues(SimpleArticle article, OntModel model, RDFNode resource) {
        article.addTextnl("{{Template:" + template.getTitle());
        for (Map.Entry<String, String> entry : template.getTemplateVariables().entrySet()) {
            Property property = model.getProperty(entry.getValue());
            String queryString =
                    "select ?property\n" +
                            "where{\n" +
                            " <" + resource.toString() + "> <" + property + "> ?property\n" +
                            "}";
            ResultSet resultSet = queryModel(model, queryString);
            if (resultSet.hasNext()) {
                RDFNode node = resultSet.next().get("?property");
                String text = node.isResource() ? "[[" + node.asResource().getLocalName() + "]]" : node.toString();
                article.addTextnl("| " + entry.getKey() + "=" + text);
            }
        }
        article.addTextnl("}}");
    }

    // pure abstraction
    protected void addCategoryFooter(SimpleArticle article, RDFNode resource, OntModel model) {
        try {
            throw new Exception("Implementation not found!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setRootRDFType(String rootRDFType) {
        this.rootRDFType = rootRDFType;
    }

    public void setTemplate(Template template) {
        this.template = template;
    }
}
