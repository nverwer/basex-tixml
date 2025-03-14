package com.rakensi.basex.xquery.functions.tixml;

import java.net.URI;
import java.net.URL;
import java.security.InvalidParameterException;
import java.util.Map;

import org.basex.query.CompileContext;
import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.QueryModule;
import org.basex.query.QueryString;
import org.basex.query.expr.Arr;
import org.basex.query.expr.Expr;
import org.basex.query.util.list.AnnList;
import org.basex.query.value.Value;
import org.basex.query.value.item.FuncItem;
import org.basex.query.value.item.QNm;
import org.basex.query.value.item.Str;
import org.basex.query.value.node.ANode;
import org.basex.query.value.node.FElem;
import org.basex.query.value.node.FNode;
import org.basex.query.value.type.FuncType;
import org.basex.query.value.type.SeqType;
import org.basex.query.var.Var;
import org.basex.query.var.VarRef;
import org.basex.query.var.VarScope;
import org.basex.util.hash.IntObjMap;
import org.basex.util.hash.TokenMap;
import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.SmaxElement;
import org.greenmercury.smax.SmaxException;
import org.greenmercury.smax.convert.DomElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import de.bottlecaps.markup.Blitz;

public class TransparentInvisibleXMLModule extends QueryModule
{

  /**
   * The transparent invisible XML function:
   TRANSPARENT_INVISIBLE_XML(FnTransparentInvisibleXml::new, "transparent-invisible-xml(grammar[,options])",
       params(STRING_ZO, MAP_ZO), FuncType.get(DOCUMENT_NODE_O, ELEMENT_O).seqType(), flag(HOF)),
   * transparent-invisible-xml($grammar as item(), $options as map(*)?)  as  function(item()) as node()*
   */
  @Requires(Permission.NONE)
  @Deterministic
  @ContextDependent
  public FuncItem transparentInvisibleXML(Object grammar, Map<String, String> options) throws QueryException {
    // Types of the arguments of the generated function.
    final Var[] generatedFunctionParameters = { new VarScope().addNew(new QNm("input"), SeqType.ITEM_O, queryContext, null) };
    // Type of the generated function.
    final FuncType generatedFunctionType = FuncType.get(SeqType.NODE_ZM, generatedFunctionParameters[0].declType);
    // The generated function.
    TransparentInvisibleXMLFunction tixmlf = new TransparentInvisibleXMLFunction(grammar, options, generatedFunctionType, queryContext);
    // Return a function item.
    return new FuncItem(null, tixmlf, generatedFunctionParameters, AnnList.EMPTY, generatedFunctionType, generatedFunctionParameters.length, null);
  }

  /**
   * The generated transparent invisible XML function.
   */
  private static final class TransparentInvisibleXMLFunction extends Arr {
    private final FuncType funcType;

    protected TransparentInvisibleXMLFunction(Object grammar, Map<String, String> options, FuncType funcType, QueryContext queryContext)
    throws QueryException
    {
      super(null, funcType.declType, parameterVars(funcType, queryContext));
      this.funcType= funcType;
      try {
          if (grammar instanceof URL) {
        	Blitz.generate(Blitz.urlContent((URL)grammar), null);
            //this.ner = new NamedEntityRecognition((URL)grammar, options, logger);
          } else if (grammar instanceof URI) {
            //this.ner = new NamedEntityRecognition(((URI)grammar).toURL(), options, logger);
          } else if (grammar instanceof Element) {
            //this.ner = new NamedEntityRecognition((Element)grammar, options, logger);
          } else if (grammar instanceof String) {
            //this.ner = new NamedEntityRecognition((String)grammar, options, logger);
          } else {
            throw new InvalidParameterException("The first parameter ($grammar) of named-entity-recognition can not be a "+grammar.getClass().getName());
          }
        } catch (Exception e) {
          throw new QueryException(e);
        }
    }

    private static Expr[] parameterVars(FuncType funcType, QueryContext queryContext)
    {
      Expr[] paramVars = new Expr[funcType.argTypes.length];
      for (int i = 0; i < paramVars.length; ++i) {
        paramVars[i] = new VarRef(null, new VarScope().addNew(new QNm("arg"+i), funcType.argTypes[i], queryContext, null));
      }
      return paramVars;
    }

    /**
     * Evaluate the generated transparent invisible XML function.
     */
    @Override
    public Value value(final QueryContext qc)
    throws QueryException
    {
      Value inputValue = arg(0).value(qc);
      // Create a SMAX document with a <wrapper> root element around the input.
      SmaxDocument smaxDocument = null;
      if (inputValue.seqType().instanceOf(SeqType.STRING_O)) {
        // Wrap the string in an element.
        final String inputString = ((Str)inputValue).toJava();
        final SmaxElement wrapper = new SmaxElement("wrapper").setStartPos(0).setEndPos(inputString.length());
        smaxDocument = new SmaxDocument(wrapper, inputString);
      } else if (inputValue.seqType().instanceOf(SeqType.NODE_O)) {
        Node inputNode = ((ANode)inputValue).toJava();
        if (inputValue.seqType().instanceOf(SeqType.DOCUMENT_NODE_O)) {
          inputNode = inputNode.getFirstChild();
        }
        Element inputElement = wrap(inputNode);
        try{
          smaxDocument = DomElement.toSmax(inputElement);
        } catch (SmaxException e) {
          throw new QueryException(e);
        }
      } else {
        throw new QueryException("The generated transparent invisible XML function accepts a string or node, but not a "+inputValue.seqType().typeString());
      }
      // Do Named Entity Recognition on the SMAX document.
      //this.ner.scan(smaxDocument);
      // Convert the SMAX document to something that BaseX can use.
      try {
        Element outputElement = DomElement.documentFromSmax(smaxDocument).getDocumentElement();
        FNode resultWrapperElement = FElem.build(outputElement, new TokenMap()).finish();
        // Remove the wrapper element and return its contents.
        Value result = resultWrapperElement.childIter().value(qc, null);
        return result;
      } catch (Exception e) {
        throw new QueryException(e);
      }
    }

    /**
     * The org.basex.api.dom.BXNode does not implement appendChild().
     * Therefore, we have to make our own wrapper element, which needs to work for org.greenmercury.smax.convert.DomElement.toSmax(Element).
     * @param node A node that must be wrapped in a "wrapper" element.
     * @return The wrapper element.
     */
    private Element wrap(Node node)
    {
      Element wrapper = new VerySimpleElementImpl("wrapper");
      wrapper.appendChild(node);
      return wrapper;
    }

    /**
     * I am not sure if this implementation is correct.
     */
    @Override
    public Expr copy(CompileContext cc, IntObjMap<Var> vm)
    {
      //return copyType(new TransparentInvisibleXMLFunction(this.ner, this.funcType, cc.qc));
    	return null;
    }

    @Override
    public void toString(QueryString qs)
    {
      qs.token("named-entity-recognition").params(exprs);
    }

  }

}
