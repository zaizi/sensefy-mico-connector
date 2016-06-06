/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.agents.transformation.mico.multimedia;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputCheckActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.system.Logging;
import org.apache.manifoldcf.agents.transformation.BaseTransformationConnector;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.interfaces.VersionContext;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.zaizi.mico.client.Injector;
import org.zaizi.mico.client.MicoClientFactory;
import org.zaizi.mico.client.exception.MicoClientException;
import org.zaizi.mico.client.model.ContentItem;
import org.zaizi.mico.client.model.ContentPart;

public class MicoExtractor extends BaseTransformationConnector {
	private static final String EDIT_SPECIFICATION_JS = "editSpecification.js";
	private static final String EDIT_SPECIFICATION_MICO_HTML = "editSpecification_MICO.html";
	private static final String VIEW_SPECIFICATION_HTML = "viewSpecification.html";
	
	private static final String MICO_PROCESSED_STATUS_FIELD = "is_processed_mico";

	protected static int maximumExtractionCharacters = 524288;

	protected static final String ACTIVITY_EXTRACT = "extract";

	protected static final String[] activitiesList = new String[] { ACTIVITY_EXTRACT };

	/** We handle up to 64K in memory; after that we go to disk. */
	protected static final long inMemoryMaximumFile = 65536;

	/**
	 * Return a list of activities that this connector generates. The connector
	 * does NOT need to be connected before this method is called.
	 * 
	 * @return the set of activities.
	 */
	@Override
	public String[] getActivitiesList() {
		return activitiesList;
	}

	/**
	 * Get a pipeline version string, given a pipeline specification object. The
	 * version string is used to uniquely describe the pertinent details of the
	 * specification and the configuration, to allow the Connector Framework to
	 * determine whether a document will need to be processed again. Note that
	 * the contents of any document cannot be considered by this method; only
	 * configuration and specification information can be considered.
	 * 
	 * This method presumes that the underlying connector object has been
	 * configured.
	 * 
	 * @param spec
	 *            is the current pipeline specification object for this
	 *            connection for the job that is doing the crawling.
	 * @return a string, of unlimited length, which uniquely describes
	 *         configuration and specification in such a way that if two such
	 *         strings are equal, nothing that affects how or whether the
	 *         document is indexed will be different.
	 */
	@Override
	public VersionContext getPipelineDescription(Specification os) throws ManifoldCFException, ServiceInterruption {
		SpecPacker sp = new SpecPacker(os);
		return new VersionContext(sp.toPackedString(), params, os);
	}

	/**
	 * Add (or replace) a document in the output data store using the connector.
	 * This method presumes that the connector object has been configured, and
	 * it is thus able to communicate with the output data store should that be
	 * necessary. The OutputSpecification is *not* provided to this method,
	 * because the goal is consistency, and if output is done it must be
	 * consistent with the output description, since that was what was partly
	 * used to determine if output should be taking place. So it may be
	 * necessary for this method to decode an output description string in order
	 * to determine what should be done.
	 * 
	 * @param documentURI
	 *            is the URI of the document. The URI is presumed to be the
	 *            unique identifier which the output data store will use to
	 *            process and serve the document. This URI is constructed by the
	 *            repository connector which fetches the document, and is thus
	 *            universal across all output connectors.
	 * @param outputDescription
	 *            is the description string that was constructed for this
	 *            document by the getOutputDescription() method.
	 * @param document
	 *            is the document data to be processed (handed to the output
	 *            data store).
	 * @param authorityNameString
	 *            is the name of the authority responsible for authorizing any
	 *            access tokens passed in with the repository document. May be
	 *            null.
	 * @param activities
	 *            is the handle to an object that the implementer of a pipeline
	 *            connector may use to perform operations, such as logging
	 *            processing activity, or sending a modified document to the
	 *            next stage in the pipeline.
	 * @return the document status (accepted or permanently rejected).
	 * @throws IOException
	 *             only if there's a stream error reading the document data.
	 */
	@Override
	public int addOrReplaceDocumentWithException(String documentURI, VersionContext pipelineDescription,
			RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
					throws ManifoldCFException, ServiceInterruption, IOException {

		Logging.agents.debug("Starting MICO extraction");

		SpecPacker sp = new SpecPacker(pipelineDescription.getSpecification());
		
		DestinationStorage ds;
	      
	    if (document.getBinaryLength() <= inMemoryMaximumFile)
	    {
	      ds = new MemoryDestinationStorage((int)document.getBinaryLength());
	    }
	    else
	    {
	      ds = new FileDestinationStorage();
	    }
		
		InputStream is = document.getBinaryStream();
		final OutputStream os = ds.getOutputStream();
		
		IOUtils.copy(is, os);
		
		// create a duplicate
		RepositoryDocument docCopy = document.duplicate();
		
		String mediaType = document.getMimeType();

		try {
			MicoClientFactory micoClientFactory = MicoConfig.getMicoClientFactory(sp.getMicoServer(), sp.getMicoUser(),
					sp.getMicoPassword());
			

			// use tika to detect mediatype
			if(mediaType.equals("application/octet-stream") || (mediaType == null) || mediaType.isEmpty()){
				Metadata metadata = new Metadata();
				TikaConfig tikaConfig = TikaConfig.getDefaultConfig();
				Detector detector = tikaConfig.getDetector();
				TikaInputStream tis = TikaInputStream.get(ds.getInputStream());
				MediaType media = detector.detect(tis, metadata);
				mediaType = media.toString();
			}
			

			if (acceptableMimeTypes.contains(mediaType.toLowerCase(Locale.ROOT))) {
				// inject to mico platform
				Injector injector = micoClientFactory.createInjectorClient();
				ContentItem ci = injector.createContentItem(mediaType, documentURI, ds.getInputStream());
				injector.submitContentItem(ci);
				
				docCopy.addField(sp.getMicoDocUriField(), ci.getUri());
				docCopy.addField(MICO_PROCESSED_STATUS_FIELD, Boolean.toString(false));
				
				Logging.agents.info("Submitted Content Item "+ci.getUri());
			}

		}catch(MicoClientException e){
			Logging.agents.error("Exception occured in Mico Client", e);
		}

		// reset original stream
		docCopy.setBinary(ds.getInputStream(), ds.getBinaryLength());

		return activities.sendDocument(documentURI, docCopy);

		// In order to be able to replay the input stream both for extraction
		// and for downstream use,
		// we need to page through it, some number of characters at a time, and
		// write those into a local buffer.
		// We can do this at the same time we're extracting, if we're clever.

		// Set up to spool back the original content, using either memory or
		// disk, whichever makes sense.
	}

	private final static Set<String> acceptableMimeTypes = new HashSet<String>();

	static {
		acceptableMimeTypes.add("video/mp4");
		acceptableMimeTypes.add("image/jpeg");
		acceptableMimeTypes.add("image/png");
	}

	/**
	 * Detect if a mime type is acceptable or not. This method is used to
	 * determine whether it makes sense to fetch a document in the first place.
	 * 
	 * @param pipelineDescription
	 *            is the document's pipeline version string, for this
	 *            connection.
	 * @param mimeType
	 *            is the mime type of the document.
	 * @param checkActivity
	 *            is an object including the activities that can be performed by
	 *            this method.
	 * @return true if the mime type can be accepted by this connector.
	 */
	@Override
	public boolean checkMimeTypeIndexable(VersionContext pipelineDescription, String mimeType,
			IOutputCheckActivity checkActivity) throws ManifoldCFException, ServiceInterruption {
		
		return super.checkMimeTypeIndexable(pipelineDescription, mimeType, checkActivity);
	}

	// ////////////////////////
	// UI Methods
	// ////////////////////////

	/**
	 * Obtain the name of the form check javascript method to call.
	 * 
	 * @param connectionSequenceNumber
	 *            is the unique number of this connection within the job.
	 * @return the name of the form check javascript method.
	 */
	@Override
	public String getFormCheckJavascriptMethodName(int connectionSequenceNumber) {
		return "s" + connectionSequenceNumber + "_checkSpecification";
	}

	/**
	 * Obtain the name of the form presave check javascript method to call.
	 * 
	 * @param connectionSequenceNumber
	 *            is the unique number of this connection within the job.
	 * @return the name of the form presave check javascript method.
	 */
	@Override
	public String getFormPresaveCheckJavascriptMethodName(int connectionSequenceNumber) {
		return "s" + connectionSequenceNumber + "_checkSpecificationForSave";
	}

	/**
	 * Output the specification header section. This method is called in the
	 * head section of a job page which has selected an output connection of the
	 * current type. Its purpose is to add the required tabs to the list, and to
	 * output any javascript methods that might be needed by the job editing
	 * HTML.
	 * 
	 * @param out
	 *            is the output to which any HTML should be sent.
	 * @param locale
	 *            is the preferred local of the output.
	 * @param os
	 *            is the current output specification for this job.
	 * @param connectionSequenceNumber
	 *            is the unique number of this connection within the job.
	 * @param tabsArray
	 *            is an array of tab names. Add to this array any tab names that
	 *            are specific to the connector.
	 */
	@Override
	public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification os,
			int connectionSequenceNumber, List<String> tabsArray) throws ManifoldCFException, IOException {
		Map<String, Object> paramMap = new HashMap<String, Object>();
		paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

		tabsArray.add(Messages.getString(locale, "MicoExtractor.MicoTabName"));

		Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_JS, paramMap);
	}

	/**
	 * Output the specification body section. This method is called in the body
	 * section of a job page which has selected an output connection of the
	 * current type. Its purpose is to present the required form elements for
	 * editing. The coder can presume that the HTML that is output from this
	 * configuration will be within appropriate <html>, <body>, and <form> tags.
	 * The name of the form is "editjob".
	 * 
	 * @param out
	 *            is the output to which any HTML should be sent.
	 * @param locale
	 *            is the preferred local of the output.
	 * @param os
	 *            is the current output specification for this job.
	 * @param connectionSequenceNumber
	 *            is the unique number of this connection within the job.
	 * @param actualSequenceNumber
	 *            is the connection within the job that has currently been
	 *            selected.
	 * @param tabName
	 *            is the current tab name.
	 */
	@Override
	public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification os, int connectionSequenceNumber,
			int actualSequenceNumber, String tabName) throws ManifoldCFException, IOException {
		Map<String, Object> paramMap = new HashMap<String, Object>();

		paramMap.put("TABNAME", tabName);
		paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));
		paramMap.put("SELECTEDNUM", Integer.toString(actualSequenceNumber));

		fillInMICOSpecificationMap(paramMap, os);
		Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_MICO_HTML, paramMap);
	}

	protected static void fillInMICOSpecificationMap(Map<String, Object> paramMap, Specification os) {
		String micoServer = "";
		String micoUser = "";
		String micoPassword = "";
		String micoDocUriField="";
		for (int i = 0; i < os.getChildCount(); i++) {
			SpecificationNode sn = os.getChild(i);
			if (sn.getType().equals(MicoConfig.NODE_MICO_SERVER)) {
				micoServer = sn.getAttributeValue(MicoConfig.ATTRIBUTE_VALUE);
				if (micoServer == null) {
					micoServer = "";
				}
			} else if (sn.getType().equals(MicoConfig.NODE_MICO_USER)) {
				micoUser = sn.getAttributeValue(MicoConfig.ATTRIBUTE_VALUE);
				if (micoUser == null) {
					micoUser = "";
				}
			} else if (sn.getType().equals(MicoConfig.NODE_MICO_PASSWORD)) {
				micoPassword = sn.getAttributeValue(MicoConfig.ATTRIBUTE_VALUE);
				if (micoPassword == null) {
					micoPassword = "";
				}
			} else if (sn.getType().equals(MicoConfig.NODE_MICO_DOC_URI_FIELD)) {
				micoDocUriField = sn.getAttributeValue(MicoConfig.ATTRIBUTE_VALUE);
				if (micoDocUriField == null) {
					micoDocUriField = "";
				}
			}
		}
		paramMap.put("MICOSERVER", micoServer);
		paramMap.put("MICOUSER", micoUser);
		paramMap.put("MICOPASSWORD", micoPassword);
		paramMap.put("MICODOCURI", micoDocUriField);
	}

	/**
	 * Process a specification post. This method is called at the start of job's
	 * edit or view page, whenever there is a possibility that form data for a
	 * connection has been posted. Its purpose is to gather form information and
	 * modify the output specification accordingly. The name of the posted form
	 * is "editjob".
	 * 
	 * @param variableContext
	 *            contains the post data, including binary file-upload
	 *            information.
	 * @param locale
	 *            is the preferred local of the output.
	 * @param os
	 *            is the current output specification for this job.
	 * @param connectionSequenceNumber
	 *            is the unique number of this connection within the job.
	 * @return null if all is well, or a string error message if there is an
	 *         error that should prevent saving of the job (and cause a
	 *         redirection to an error page).
	 */
	@Override
	public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification os,
			int connectionSequenceNumber) throws ManifoldCFException {
		String seqPrefix = "s" + connectionSequenceNumber + "_";

		SpecificationNode node = new SpecificationNode(MicoConfig.NODE_MICO_SERVER);
		String micoserver = variableContext.getParameter(seqPrefix + "micoserver");
		if (micoserver != null) {
			node.setAttribute(MicoConfig.ATTRIBUTE_VALUE, micoserver);
		} else {
			node.setAttribute(MicoConfig.ATTRIBUTE_VALUE, "");
		}
		os.addChild(os.getChildCount(), node);

		node = new SpecificationNode(MicoConfig.NODE_MICO_USER);
		String micouser = variableContext.getParameter(seqPrefix + "micouser");
		if (micouser != null) {
			node.setAttribute(MicoConfig.ATTRIBUTE_VALUE, micouser);
		} else {
			node.setAttribute(MicoConfig.ATTRIBUTE_VALUE, "");
		}
		os.addChild(os.getChildCount(), node);

		node = new SpecificationNode(MicoConfig.NODE_MICO_PASSWORD);
		String micopassword = variableContext.getParameter(seqPrefix + "micopassword");
		if (micopassword != null) {
			node.setAttribute(MicoConfig.ATTRIBUTE_VALUE, micopassword);
		} else {
			node.setAttribute(MicoConfig.ATTRIBUTE_VALUE, "");
		}
		os.addChild(os.getChildCount(), node);
		
		node = new SpecificationNode(MicoConfig.NODE_MICO_DOC_URI_FIELD);
		String micodocurifield = variableContext.getParameter(seqPrefix + "micodocuri");
		if (micodocurifield != null) {
			node.setAttribute(MicoConfig.ATTRIBUTE_VALUE, micodocurifield);
		} else {
			node.setAttribute(MicoConfig.ATTRIBUTE_VALUE, "");
		}
		os.addChild(os.getChildCount(), node);

		return null;
	}

	/**
	 * View specification. This method is called in the body section of a job's
	 * view page. Its purpose is to present the output specification information
	 * to the user. The coder can presume that the HTML that is output from this
	 * configuration will be within appropriate <html> and <body> tags.
	 * 
	 * @param out
	 *            is the output to which any HTML should be sent.
	 * @param locale
	 *            is the preferred local of the output.
	 * @param connectionSequenceNumber
	 *            is the unique number of this connection within the job.
	 * @param os
	 *            is the current output specification for this job.
	 */
	@Override
	public void viewSpecification(IHTTPOutput out, Locale locale, Specification os, int connectionSequenceNumber)
			throws ManifoldCFException, IOException {
		Map<String, Object> paramMap = new HashMap<String, Object>();
		paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

		fillInMICOSpecificationMap(paramMap, os);
		Messages.outputResourceWithVelocity(out, locale, VIEW_SPECIFICATION_HTML, paramMap);
	}

	protected static int handleIOException(IOException e) throws ManifoldCFException {
		// IOException reading from our local storage...
		if (e instanceof InterruptedIOException)
			throw new ManifoldCFException(e.getMessage(), e, ManifoldCFException.INTERRUPTED);
		throw new ManifoldCFException(e.getMessage(), e);
	}
	
	protected static interface DestinationStorage
	  {
	    /** Get the output stream to write to.  Caller should explicitly close this stream when done writing.
	    */
	    public OutputStream getOutputStream()
	      throws ManifoldCFException;
	    
	    /** Get new binary length.
	    */
	    public long getBinaryLength()
	      throws ManifoldCFException;

	    /** Get the input stream to read from.  Caller should explicitly close this stream when done reading.
	    */
	    public InputStream getInputStream()
	      throws ManifoldCFException;
	    
	    /** Close the object and clean up everything.
	    * This should be called when the data is no longer needed.
	    */
	    public void close()
	      throws ManifoldCFException;
	  }
	
	protected static class FileDestinationStorage implements DestinationStorage
	  {
	    protected final File outputFile;
	    protected final OutputStream outputStream;

	    public FileDestinationStorage()
	      throws ManifoldCFException
	    {
	      File outputFile;
	      OutputStream outputStream;
	      try
	      {
	        outputFile = File.createTempFile("mcfmico","tmp");
	        outputStream = new FileOutputStream(outputFile);
	      }
	      catch (IOException e)
	      {
	        handleIOException(e);
	        outputFile = null;
	        outputStream = null;
	      }
	      this.outputFile = outputFile;
	      this.outputStream = outputStream;
	    }
	    
	    @Override
	    public OutputStream getOutputStream()
	      throws ManifoldCFException
	    {
	      return outputStream;
	    }
	    
	    /** Get new binary length.
	    */
	    @Override
	    public long getBinaryLength()
	      throws ManifoldCFException
	    {
	      return outputFile.length();
	    }

	    /** Get the input stream to read from.  Caller should explicitly close this stream when done reading.
	    */
	    @Override
	    public InputStream getInputStream()
	      throws ManifoldCFException
	    {
	      try
	      {
	        return new FileInputStream(outputFile);
	      }
	      catch (IOException e)
	      {
	        handleIOException(e);
	        return null;
	      }
	    }
	    
	    /** Close the object and clean up everything.
	    * This should be called when the data is no longer needed.
	    */
	    @Override
	    public void close()
	      throws ManifoldCFException
	    {
	      outputFile.delete();
	    }
	  }
	
	protected static class MemoryDestinationStorage implements DestinationStorage
	  {
	    protected final ByteArrayOutputStream outputStream;
	    
	    public MemoryDestinationStorage(int sizeHint)
	    {
	      outputStream = new ByteArrayOutputStream(sizeHint);
	    }
	    
	    @Override
	    public OutputStream getOutputStream()
	      throws ManifoldCFException
	    {
	      return outputStream;
	    }

	    /** Get new binary length.
	    */
	    @Override
	    public long getBinaryLength()
	      throws ManifoldCFException
	    {
	      return outputStream.size();
	    }
	    
	    /** Get the input stream to read from.  Caller should explicitly close this stream when done reading.
	    */
	    @Override
	    public InputStream getInputStream()
	      throws ManifoldCFException
	    {
	      return new ByteArrayInputStream(outputStream.toByteArray());
	    }
	    
	    /** Close the object and clean up everything.
	    * This should be called when the data is no longer needed.
	    */
	    public void close()
	      throws ManifoldCFException
	    {
	    }

	  }

	protected static class SpecPacker {

		private final String micoServer;
		private final String micoUser;
		private final String micoPassword;
		private final String micoDocUriField;

		public SpecPacker(Specification os) {

			String micoServer = null;
			String micoUser = null;
			String micoPassword = null;
			String micoDocUriField = null;
			
			for (int i = 0; i < os.getChildCount(); i++) {
				SpecificationNode sn = os.getChild(i);
				if (sn.getType().equals(MicoConfig.NODE_MICO_SERVER)) {
					micoServer = sn.getAttributeValue(MicoConfig.ATTRIBUTE_VALUE);
				} else if (sn.getType().equals(MicoConfig.NODE_MICO_USER)) {
					micoUser = sn.getAttributeValue(MicoConfig.ATTRIBUTE_VALUE);
				} else if (sn.getType().equals(MicoConfig.NODE_MICO_PASSWORD)) {
					micoPassword = sn.getAttributeValue(MicoConfig.ATTRIBUTE_VALUE);
				} else if (sn.getType().equals(MicoConfig.NODE_MICO_DOC_URI_FIELD)) {
					micoDocUriField = sn.getAttributeValue(MicoConfig.ATTRIBUTE_VALUE);
				}

			}
			this.micoServer = micoServer;
			this.micoUser = micoUser;
			this.micoPassword = micoPassword;
			this.micoDocUriField = micoDocUriField;
		}

		public String toPackedString() {
			StringBuilder sb = new StringBuilder();
			if (micoServer != null) {
				sb.append('+');
				sb.append(micoServer);
			} else {
				sb.append('-');
			}
			if (micoUser != null) {
				sb.append('+');
				sb.append(micoUser);
			} else {
				sb.append('-');
			}
			if (micoPassword != null) {
				sb.append('+');
				sb.append(micoPassword);
			} else {
				sb.append('-');
			}
			if (micoDocUriField != null) {
				sb.append('+');
				sb.append(micoDocUriField);
			} else {
				sb.append('-');
			}
			return sb.toString();
		}

		public String getMicoServer() {
			return micoServer;
		}

		public String getMicoUser() {
			return micoUser;
		}

		public String getMicoPassword() {
			return micoPassword;
		}
		
		public String getMicoDocUriField(){
			return micoDocUriField;
		}

	}

}
