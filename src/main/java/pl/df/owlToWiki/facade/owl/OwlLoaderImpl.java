package pl.df.owlToWiki.facade.owl;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.reasoner.Reasoner;
import com.hp.hpl.jena.reasoner.ReasonerRegistry;
import net.sourceforge.jwbf.core.contentRep.SimpleArticle;
import org.apache.log4j.Logger;

import java.util.LinkedList;
import java.util.List;

/**
 * User: dominikfilipiak
 * Date: 24/02/2014
 * Time: 12:56
 */
public class OwlLoaderImpl implements OwlLoader {

    private List<String> inputFiles;
    private String rootRDFType;
    private List<String> predicates;
    private static Logger LOGGER = Logger.getLogger(OwlLoaderImpl.class);


    @Override
    public List<SimpleArticle> getArticlesToWrite() throws OntLoaderException {
        OntModel model = loadFilesAsOntologyModel();
        checkRootClass(model);
        List<SimpleArticle> categoryArticles = getCategoryArticles(model);
        List<SimpleArticle> individualsArticles = getIndividualsArticles(model);
        // TODO: Extract to article factory - regular, categoryArticleFactory
        List<SimpleArticle> articles = new LinkedList<>();
        articles.addAll(categoryArticles);
        articles.addAll(individualsArticles);

        return articles;
    }


    private List<SimpleArticle> getIndividualsArticles(OntModel model) {
        LOGGER.info("Searching for individuals...");
        List<SimpleArticle> articles = new LinkedList<>();
        String queryString = "PREFIX owl:<http://www.w3.org/2002/07/owl#>\n" +
                "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>\n" +
                "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "select DISTINCT ?individual\n" +
                "where{\n" +
                "?individual a owl:NamedIndividual.\n" +
                "?individual rdf:type ?type.\n" +
                "?type ((owl:equivalentClass/owl:intersectionOf/rdf:rest*/rdf:first)|rdfs:subClassOf)* <" + rootRDFType + ">.\n" +
                "FILTER (isURI(?individual) && !isBLANK(?individual)).\n" +
                "}";

        ResultSet resultSet = queryModel(model, queryString);

        while (resultSet.hasNext()) {
            QuerySolution next = resultSet.next();
            RDFNode individual = next.get("?individual");
            if (individual != null) {
                LOGGER.info(individual.toString());
                SimpleArticle simpleArticle = prepareIndividualArticle(model, individual);
                articles.add(simpleArticle);
            }
        }
        return articles;
    }

    private SimpleArticle prepareIndividualArticle(OntModel model, RDFNode individual) {
        final String title = individual.asResource().getLocalName();
        SimpleArticle article = new SimpleArticle();
        article.setTitle(title);
        Property definition = model.getProperty("http://www.e-lico.eu/ontologies/dmo/DMOP/DMOP.owl#definition");
        String queryString =
                "select ?definition\n" +
                        "where{\n" +
                        " <" + individual.toString() + "> <" + definition + "> ?definition\n" +
                        "}";
        ResultSet resultSet = queryModel(model, queryString);
        if (resultSet.hasNext()) {
            article.addTextnl(resultSet.next().get("?definition").toString());
        }

        // TODO: bad query -> SET IT TO RDF NODE
        queryString = "PREFIX owl:<http://www.w3.org/2002/07/owl#>\n" +
                "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>\n" +
                "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "select DISTINCT ?parent\n" +
                "where{\n" +
                "\n" +
                "<" + individual + "> (owl:equivalentClass|(owl:equivalentClass/owl:intersectionOf/rdf:rest*/rdf:first)|rdfs:subClassOf)+ ?parent.\n" +
                "FILTER (isURI(?parent) && !isBLANK(?parent)).\n" +
                "}";
        resultSet = queryModel(model, queryString);
        while (resultSet.hasNext()) {
            article.addTextnl("[[Category:" + resultSet.next().get("?parent").asResource().getLocalName() + "]]");
        }
        return article;

    }


    /**
     * Prepares list of category articles.
     *
     * @param model Ontology model
     */
    private List<SimpleArticle> getCategoryArticles(OntModel model) {
        List<SimpleArticle> articles = new LinkedList<>();
        String queryString = "PREFIX owl:<http://www.w3.org/2002/07/owl#>\n" +
                "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>\n" +
                "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "select ?subclass\n" +
                "where {\n" +
                " {\n" +
                "  ?subclass ((owl:equivalentClass/owl:intersectionOf/rdf:rest*/rdf:first)|rdfs:subClassOf)+ <" + rootRDFType + ">.\n" +
                " } UNION {\n" +
                " }\n" +
                "}";          // TODO: sprawdzić, czy potrzebne będzie samo equivalentclass

        ResultSet resultSet = queryModel(model, queryString);

        while (resultSet.hasNext()) {
            QuerySolution next = resultSet.next();
            RDFNode categoryClass = next.get("?subclass");
            if (categoryClass != null) {
                LOGGER.info(categoryClass.toString());
                SimpleArticle simpleArticle = prepareCategoryArticle(model, categoryClass);
                articles.add(simpleArticle);
            }
        }
        return articles;
    }

    /**
     * Checks root ontology class. If class does not exist, OntLoaderException will be thrown.
     *
     * @param model Ontology model
     * @throws OntLoaderException
     */
    private void checkRootClass(OntModel model) throws OntLoaderException {
        OntClass ontClass = model.getOntClass(this.rootRDFType);
        if (ontClass == null) {
            throw new OntLoaderException("Can't find root RDF class. Check your config file.");
        }
    }

    /**
     * Queries ontology model via SPARQL query
     *
     * @param model       Ontology model
     * @param queryString Query string
     * @return A result set
     */
    private ResultSet queryModel(OntModel model, String queryString) {
        Query query = QueryFactory.create(queryString);
        QueryExecution queryExe = QueryExecutionFactory.create(query, model);
        return queryExe.execSelect();
    }

    /**
     * Prepares single category article
     *
     * @param model    Ontology model
     * @param resource Statement
     * @return Article
     */
    private SimpleArticle prepareCategoryArticle(OntModel model, RDFNode resource) {
        final String title = resource.asResource().getLocalName();
        SimpleArticle article = new SimpleArticle();
        article.setTitle("Category:" + title);
        Property definition = model.getProperty("http://www.e-lico.eu/ontologies/dmo/DMOP/DMOP.owl#definition");
        String queryString =
                "select ?definition\n" +
                        "where{\n" +
                        " <" + resource.toString() + "> <" + definition + "> ?definition\n" +
                        "}";
        ResultSet resultSet = queryModel(model, queryString);
        if (resultSet.hasNext()) {
            article.addTextnl(resultSet.next().get("?definition").toString());
        }

        // TODO: bad query -> SET IT TO RDF NODE
        queryString = "PREFIX owl:<http://www.w3.org/2002/07/owl#>\n" +
                "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>\n" +
                "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "select DISTINCT ?parent\n" +
                "where{\n" +
                "\n" +
                "<" + resource + "> (owl:equivalentClass|(owl:equivalentClass/owl:intersectionOf/rdf:rest*/rdf:first)|rdfs:subClassOf)+ ?parent.\n" +
                "FILTER (isURI(?parent) && !isBLANK(?parent)).\n" +
                "}";
        resultSet = queryModel(model, queryString);
        while (resultSet.hasNext()) {
            article.addTextnl("[[Category:" + resultSet.next().get("?parent").asResource().getLocalName() + "]]");
        }

//        model.getProperty(resource, definiotion);
//        article.addTextnl();

//        for (String predicate : predicates) {
            // TODO:
//            final Property property = model.getProperty(predicate);
//            final RDFNode propertyResourceValue = resource.getPropertyValue(property);
//            article.addText(propertyResourceValue.asLiteral().getString());
//        }
        return article;
    }

    /**
     * Loads all files from properties
     *
     * @return Ontology model
     */
    private OntModel loadFilesAsOntologyModel() {
        OntModel ontModel = ModelFactory.createOntologyModel();
        for (String filePath : inputFiles) {
            LOGGER.info("Attempting to read " + filePath);
            loadFile(filePath, ontModel);
        }
        LOGGER.debug("Done with loading files.");
        LOGGER.debug("Setting up the reasoner.");
        Reasoner reasoner = ReasonerRegistry.getTransitiveReasoner();
        reasoner = reasoner.bindSchema(ontModel);
        LOGGER.debug("Done with setting up the reasoner.");
        OntModelSpec ontModelSpec = OntModelSpec.OWL_LITE_MEM_TRANS_INF;
        ontModelSpec.setReasoner(reasoner);
        OntModel model = ModelFactory.createOntologyModel(ontModelSpec, ontModel);
        LOGGER.debug("Ontology model created.");
        return model;
    }

    /**
     * Loads single file
     *
     * @param filePath Absolute path to file
     * @param ontology Ontology to append mentioned file to
     */
    private void loadFile(String filePath, OntModel ontology) {
        ontology.read(filePath);
    }

    @Override
    public void setInputFiles(List<String> inputFiles) {
        this.inputFiles = inputFiles;
    }

    @Override
    public void setRootRDFType(String rootRDFType) {
        this.rootRDFType = rootRDFType;
    }

    @Override
    public void setPredicates(List<String> predicates) {
        this.predicates = predicates;
    }
}
