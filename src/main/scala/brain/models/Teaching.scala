/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Israel Freitas -- ( gmail => israel.araujo.freitas)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package brain.models

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import com.ansvia.graph.BlueprintsWrapper._
import com.ansvia.graph.annotation.Persistent
import net.liftweb.json._
import net.liftweb.common.Box
import net.liftweb.util.Helpers
import com.tinkerpop.blueprints.Graph
import com.tinkerpop.blueprints.Vertex
import brain.db.GraphDb
import com.tinkerpop.blueprints.TransactionalGraph

import aimltoxml.aiml.TemplateElement
import aimltoxml.aiml.Text
import aimltoxml.aiml.Category
import aimltoxml.aiml.Srai

case class Teaching(whenTheUserSays:String, say:String) extends DbObject{
    require(!whenTheUserSays.isEmpty, "Field 'when the user says', can not be empty.")
    require(!say.isEmpty, "Field 'say', can not be empty.")
    
    var id:Option[String] = None
	var informationId:Option[String] = None
	
	@Persistent var respondingTo:String = null
	@Persistent var memorize:String = null
    
    def toAiml = new TeachingToCategoryAdapter(this).toCategory
    
    def save()(implicit db:TransactionalGraph) = transact{
        if(this.respondingTo == null || this.respondingTo.trim().equals("")) this.respondingTo = null
        if(this.memorize == null || this.memorize.trim().equals("")) this.memorize = null
        
        val that = super.save()
        db.getVertex(informationId.get) --> "include" --> that
        that
    }
    
    def destroy() (implicit db:TransactionalGraph) = transact { 
        db removeVertex getVertex 
    }

}

object Teaching extends PersistentName {
    private implicit val formats = net.liftweb.json.DefaultFormats

    implicit def toJson(teaching: Teaching): JValue = JObject(
        JField("id", JString(teaching.id.get.replace("#", ""))) ::
        JField("whenTheUserSays", JString(teaching.whenTheUserSays)) 		::
        JField("respondingTo", JString(teaching.respondingTo)) 			::
        JField("memorize", JString(teaching.memorize)) 				::
        JField("say", JString(teaching.say)) 					::
        JField("informationId", JString(teaching.informationId.get.replace("#", ""))) :: 
        Nil
    )
    
    implicit def informationSetToJValue(informations: Set[Teaching]): JValue = JArray(informations.map(toJson).toList)
    
    def findAll()(implicit db:Graph):Set[Knowledge] = query().vertices().toSet[Vertex].map(v=>Knowledge(v))
    
    def findById(id:String)(implicit db:Graph):Teaching = Teaching(db.getVertex(id))
    
    def findByInformation(information:Information)(implicit db:Graph):Set[Teaching] = information.getVertex.pipe.out("include").iterator.toSet[Vertex].map(v=>Teaching(v))
    
    def apply(in: JValue):Box[Teaching] = Helpers.tryo{
        try {
	        val id = (in \ "id") match {
        	    case id: JString => Some(id.values)
        	    case _ =>  None
        	}
	        val informationId = (in \ "informationId") match {
        	    case informationId: JString => Some(informationId.values)
        	    case _ => None
        	}
	        val whenTheUserSays = (in \ "whenTheUserSays") match {
		        case whenTheUserSays: JString => whenTheUserSays.values
		        case _ =>  ""
	        }
	        val respondingTo = (in \ "respondingTo") match {
		        case respondingTo: JString => Some(respondingTo.values)
		        case _ =>  None
	        }
	        val memorize = (in \ "memorize") match {
		        case memorize: JString => Some(memorize.values)
		        case _ =>  None
	        }
	        val say = (in \ "say") match {
		        case say: JString => say.values
		        case _ =>  ""
	        }
	        
	        val teaching = Teaching(whenTheUserSays, say)
	        teaching.id = id
	        teaching.informationId = informationId
	        respondingTo map { teaching.respondingTo = _}
	        memorize map { teaching.memorize = _}
	        teaching
		}
        catch{
            case t:Throwable => t.printStackTrace(); throw t
        }
    }
    def unapply(in:JValue):Option[Teaching] = apply(in)
    
    def unapply(in:Any):Option[(Option[String], Option[String], Option[String], Option[String], String, String)] = {
        in match {
            case teaching : Teaching => {
               val respondingTo = if(teaching.respondingTo == null) None else Some(teaching.respondingTo)
               val memorize = if(teaching.memorize == null) None else Some(teaching.memorize)
               Some((teaching.id, teaching.informationId, respondingTo, memorize, teaching.whenTheUserSays, teaching.say))
            }
            case id : String => {
            	implicit val db = GraphDb.get
				try{
					val teaching = Teaching.findById(id)
	        		val respondingTo = if(teaching.respondingTo == null) None else Some(teaching.respondingTo)
	        		val memorize = if(teaching.memorize == null) None else Some(teaching.memorize)
	        		Some((teaching.id, teaching.informationId, respondingTo, memorize, teaching.whenTheUserSays, teaching.say))
				}
	        	catch{
	        	    case t: Throwable => None
	        	}
	        	finally{
	        		db.shutdown()
	        	}
            }
            case _ => None
        }
    }
    
    def apply(vertex:Vertex)(implicit db:Graph):Teaching = {
        val teaching = vertex.toCC[Teaching].get
        teaching.id = Some(vertex.getId.toString)
        teaching.informationId = Some(vertex.pipe.in("include").iterator.next().getId().toString())
        teaching
    }
}

class TeachingToCategoryAdapter(teaching: Teaching) {

    val whatWasSaid: Set[String] = teaching.whenTheUserSays.split("\r\n").toSet[String].filter(!_.isEmpty)
    val whatToSay: Set[String] = teaching.say.split("\r\n").toSet[String].filter(!_.isEmpty)
    val respondingTo = teaching.respondingTo

    def selectDefaultPattern(setOfWhatWasSaid: Set[String]) = {
        var defaultPattern         = ""
        var lowerPatternComplexity = 100.0
        var patternComplexity      = 100.0

        setOfWhatWasSaid.foreach { whatWasSaid =>
            patternComplexity = calculateThePatternComplexity(whatWasSaid)
            if (patternComplexity < lowerPatternComplexity) {
                lowerPatternComplexity = patternComplexity
                defaultPattern = whatWasSaid
            }
        }
        defaultPattern
    }

    // it should be a selectDefaultPattern's local function, but is not for tests purposes.
    def calculateThePatternComplexity(pattern: String): Double = {
        def countStarsIn(p: String) = countSpecialChar("*", p)
        def countUnderscoreIn(p: String) = countSpecialChar("_", p)

        val amountOfChar = pattern.length
        val amountOfStar = countStarsIn(pattern)
        val amountOfUnderscore = countUnderscoreIn(pattern)

        amountOfChar * 0.001 + amountOfStar * 1 + amountOfUnderscore * 1
    }

    // it should be a calculateThePatternComplexity's local function, but is not for tests purposes.
    def countSpecialChar(c: String, p: String) = { p.split("\\" + c + "+", -1).size - 1 }

    def createCategory(whatWasSaid: String, defaultPattern: String, respondingTo: String, say: Set[String]) = {
        if (whatWasSaid == defaultPattern) Category(whatWasSaid, createTemplateElements(say))
        else Category(whatWasSaid, Set(Srai(defaultPattern)))
    }

    def createTemplateElements(say: Set[String]): Set[TemplateElement] = say.map { Text(_) }

    def toCategory: Set[Category] = {
        val defaultPattern = selectDefaultPattern(whatWasSaid)
        whatWasSaid.map(createCategory(_, defaultPattern, respondingTo, whatToSay))
    }
}

//object Teaching extends PersistentName {
//    
//    def vertexToTeaching(vertex:Vertex):Teaching=Teaching(vertex)
//    
//	def findById(id:String)(implicit db:Graph):Option[Teaching]  = {
//	    Some(Teaching(query().has("id", id).vertices().head))
//	}
//	def findByInformationId(informationId:Any)(implicit db:Graph):Set[Teaching]  = {
//	     query().has("in_", informationId).vertices().toSet[Vertex].map(v=>Teaching(v))
//	}
////    def create(teaching:Teaching)(implicit db:OrientGraph):Option[Teaching] = {
////    	teaching.save.toCC[Teaching]
//////        val result = GraphDb.transaction[Vertex]({
//////        	val teachingVertex    = db.addVertex("class:Teaching", "whenTheUserSays", teaching.whenTheUserSays, "respondingTo", teaching.respondingTo, "memorize", teaching.memorize, "say", teaching.say)
//////            val informationVertex = db.getVertex(teaching.informationId)
//////            db.addEdge("class:Include", informationVertex, teachingVertex, "include")
//////            teachingVertex
//////        }).get
//////        //result.map(v=>Teaching(v)) // TODO: a orient inconsistent behavior (sometimes the vertex came without all properties) force me to adopt other ways to construct the returning Teaching.
//////        Some(Teaching(result.getId().toString(), teaching.informationId, teaching.whenTheUserSays, teaching.respondingTo, teaching.memorize, teaching.say))
////	}
////    def update(teaching:Teaching)(implicit db:OrientGraph):Option[Teaching] = {
////        transact{
////            var teachingVertex = db.getVertex(teaching.id)
////            teachingVertex.setProperty("whenTheUserSays", teaching.whenTheUserSays)
////            teachingVertex.setProperty("respondingTo", teaching.respondingTo)
////            teachingVertex.setProperty("memorize", teaching.memorize)
////            teachingVertex.setProperty("say", teaching.say)
////            teachingVertex.toCC[Teaching]
////        }
////        
//////        val result = GraphDb.transaction[Vertex]({
//////            var teachingVertex = db.getVertex(teaching.id)
//////            teachingVertex.setProperty("whenTheUserSays", teaching.whenTheUserSays)
//////            teachingVertex.setProperty("respondingTo", teaching.respondingTo)
//////            teachingVertex.setProperty("memorize", teaching.memorize)
//////            teachingVertex.setProperty("say", teaching.say)
//////            teachingVertex
//////        }).get
//////        //result.map(v=>Teaching(v)) // TODO: a orient inconsistent behavior (sometimes the vertex came without all properties) force me to adopt other ways to construct the returning Teaching.
//////        Some(Teaching(result.getId().toString(), teaching.informationId, teaching.whenTheUserSays, teaching.respondingTo, teaching.memorize, teaching.say))
////    }
////    def delete(id:String)(implicit db:OrientGraph):Option[Teaching] = {
////        transact{
////            val sqlString = raw"select from (traverse out() from  $id)";
////	        val vertices:java.lang.Iterable[Vertex] = db.command(new OSQLSynchQuery[Vertex](sqlString)).execute();
////	        val teachingVertex = vertices.head
////	        val teaching = Teaching(teachingVertex)// after the commit, the teachingVertex last all your properties.
////	        db.removeVertex(teachingVertex)
////	        Some(teaching)
////        }
//////        GraphDb.transaction[Teaching]({
//////	        val sqlString = raw"select from (traverse out() from  $id)";
//////	        val vertices:java.lang.Iterable[Vertex] = db.command(new OSQLSynchQuery[Vertex](sqlString)).execute();
//////	        val teachingVertex = vertices.head
//////	        val teaching = Teaching(teachingVertex)// after the commit, the teachingVertex last all your properties.
//////	        db.removeVertex(teachingVertex)
//////	        teaching
//////        })
////    }
//    
//    def apply(vertex:Vertex):Teaching = Teaching(vertex.getProperty("whenTheUserSays"), vertex.getProperty("respondingTo"), vertex.getProperty("memorize"), vertex.getProperty("say")) 
//}




