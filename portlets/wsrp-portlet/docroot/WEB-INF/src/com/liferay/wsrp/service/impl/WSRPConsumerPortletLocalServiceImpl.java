/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.wsrp.service.impl;

import com.liferay.counter.kernel.service.CounterLocalServiceUtil;
import com.liferay.portal.kernel.cluster.Clusterable;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.InvokerPortlet;
import com.liferay.portal.kernel.portlet.PortletBag;
import com.liferay.portal.kernel.portlet.PortletBagPool;
import com.liferay.portal.kernel.portlet.PortletInstanceFactoryUtil;
import com.liferay.portal.kernel.systemevent.SystemEvent;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.Transactional;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Tuple;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.uuid.PortalUUIDUtil;
import com.liferay.portal.kernel.xml.Namespace;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.model.Portlet;
import com.liferay.portal.model.PortletApp;
import com.liferay.portal.model.PortletInfo;
import com.liferay.portal.model.SystemEventConstants;
import com.liferay.portal.service.PortletLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.util.PortalUtil;
import com.liferay.wsrp.consumer.portlet.ConsumerFriendlyURLMapper;
import com.liferay.wsrp.consumer.portlet.ConsumerPortlet;
import com.liferay.wsrp.exception.NoSuchConsumerPortletException;
import com.liferay.wsrp.exception.WSRPConsumerPortletHandleException;
import com.liferay.wsrp.exception.WSRPConsumerPortletNameException;
import com.liferay.wsrp.model.WSRPConsumer;
import com.liferay.wsrp.model.WSRPConsumerPortlet;
import com.liferay.wsrp.service.ClpSerializer;
import com.liferay.wsrp.service.base.WSRPConsumerPortletLocalServiceBaseImpl;
import com.liferay.wsrp.util.ExtensionHelperUtil;
import com.liferay.wsrp.util.LocalizedStringUtil;
import com.liferay.wsrp.util.WSRPConsumerManager;
import com.liferay.wsrp.util.WSRPConsumerManagerFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.namespace.QName;

import oasis.names.tc.wsrp.v2.types.MarkupType;
import oasis.names.tc.wsrp.v2.types.ParameterDescription;
import oasis.names.tc.wsrp.v2.types.PortletDescription;

import org.apache.axis.message.MessageElement;

/**
 * @author Brian Wing Shun Chan
 */
public class WSRPConsumerPortletLocalServiceImpl
	extends WSRPConsumerPortletLocalServiceBaseImpl {

	public WSRPConsumerPortlet addWSRPConsumerPortlet(
			long wsrpConsumerId, String name, String portletHandle,
			ServiceContext serviceContext)
		throws PortalException {

		WSRPConsumer wsrpConsumer = wsrpConsumerPersistence.findByPrimaryKey(
			wsrpConsumerId);
		Date now = new Date();

		validate(name, portletHandle);

		long wsrpConsumerPortletId = CounterLocalServiceUtil.increment();

		WSRPConsumerPortlet wsrpConsumerPortlet =
			wsrpConsumerPortletPersistence.create(wsrpConsumerPortletId);

		wsrpConsumerPortlet.setUuid(serviceContext.getUuid());
		wsrpConsumerPortlet.setCompanyId(wsrpConsumer.getCompanyId());
		wsrpConsumerPortlet.setCreateDate(now);
		wsrpConsumerPortlet.setModifiedDate(now);
		wsrpConsumerPortlet.setWsrpConsumerId(wsrpConsumerId);
		wsrpConsumerPortlet.setName(name);
		wsrpConsumerPortlet.setPortletHandle(portletHandle);

		wsrpConsumerPortletPersistence.update(wsrpConsumerPortlet);

		wsrpConsumerPortletLocalService.initWSRPConsumerPortlet(
			wsrpConsumer.getCompanyId(), wsrpConsumerId, wsrpConsumerPortletId,
			wsrpConsumerPortlet.getUuid(), name, portletHandle);

		return wsrpConsumerPortlet;
	}

	public WSRPConsumerPortlet addWSRPConsumerPortlet(
			String wsrpConsumerUuid, String name, String portletHandle,
			ServiceContext serviceContext)
		throws PortalException {

		WSRPConsumer wsrpConsumer = wsrpConsumerLocalService.getWSRPConsumer(
			wsrpConsumerUuid);

		return addWSRPConsumerPortlet(
			wsrpConsumer.getWsrpConsumerId(), name, portletHandle,
			serviceContext);
	}

	@Override
	public WSRPConsumerPortlet deleteWSRPConsumerPortlet(
			long wsrpConsumerPortletId)
		throws PortalException {

		WSRPConsumerPortlet wsrpConsumerPortlet =
			wsrpConsumerPortletPersistence.findByPrimaryKey(
				wsrpConsumerPortletId);

		return wsrpConsumerPortletLocalService.deleteWSRPConsumerPortlet(
			wsrpConsumerPortlet);
	}

	public void deleteWSRPConsumerPortlet(String wsrpConsumerPortletUuid)
		throws PortalException {

		List<WSRPConsumerPortlet> wsrpConsumerPortlets =
			wsrpConsumerPortletPersistence.findByUuid(wsrpConsumerPortletUuid);

		if (!wsrpConsumerPortlets.isEmpty()) {
			wsrpConsumerPortletLocalService.deleteWSRPConsumerPortlet(
				wsrpConsumerPortlets.get(0));
		}
	}

	@Override
	@SystemEvent(type = SystemEventConstants.TYPE_DELETE)
	public WSRPConsumerPortlet deleteWSRPConsumerPortlet(
			WSRPConsumerPortlet wsrpConsumerPortlet)
		throws PortalException {

		wsrpConsumerPortletPersistence.remove(wsrpConsumerPortlet);

		WSRPConsumer wsrpConsumer = wsrpConsumerPersistence.findByPrimaryKey(
			wsrpConsumerPortlet.getWsrpConsumerId());

		wsrpConsumerPortletLocalService.destroyWSRPConsumerPortlet(
			wsrpConsumerPortlet.getWsrpConsumerPortletId(),
			wsrpConsumerPortlet.getUuid(), wsrpConsumer.getUrl());

		return wsrpConsumerPortlet;
	}

	public void deleteWSRPConsumerPortlets(long wsrpConsumerId)
		throws PortalException {

		List<WSRPConsumerPortlet> wsrpConsumerPortlets =
			wsrpConsumerPortletPersistence.findByWsrpConsumerId(wsrpConsumerId);

		for (WSRPConsumerPortlet wsrpConsumerPortlet : wsrpConsumerPortlets) {
			wsrpConsumerPortletLocalService.deleteWSRPConsumerPortlet(
				wsrpConsumerPortlet);
		}
	}

	@Clusterable
	public void destroyWSRPConsumerPortlet(
		long wsrpConsumerPortletId, String wsrpConsumerPortletUuid,
		String url) {

		try {
			Portlet portlet = _portletsPool.remove(wsrpConsumerPortletUuid);

			if (portlet == null) {
				WSRPConsumerPortlet wsrpConsumerPortlet =
					getWSRPConsumerPortlet(wsrpConsumerPortletId);

				portlet = PortletLocalServiceUtil.getPortletById(
					wsrpConsumerPortlet.getCompanyId(),
					getPortletId(wsrpConsumerPortletUuid));
			}
			else {
				WSRPConsumerManagerFactory.destroyWSRPConsumerManager(url);

				_failedWSRPConsumerPortlets.remove(wsrpConsumerPortletId);
			}

			PortletInstanceFactoryUtil.destroy(portlet);
		}
		catch (Exception e) {
			_log.error(
				"Unable to destroy WSRP consumer portlet " +
					wsrpConsumerPortletId,
				e);
		}
	}

	public void destroyWSRPConsumerPortlets() throws PortalException {
		List<WSRPConsumerPortlet> wsrpConsumerPortlets =
			wsrpConsumerPortletPersistence.findAll();

		for (WSRPConsumerPortlet wsrpConsumerPortlet : wsrpConsumerPortlets) {
			WSRPConsumer wsrpConsumer =
				wsrpConsumerPersistence.findByPrimaryKey(
					wsrpConsumerPortlet.getWsrpConsumerId());

			destroyWSRPConsumerPortlet(
				wsrpConsumerPortlet.getWsrpConsumerPortletId(),
				wsrpConsumerPortlet.getUuid(), wsrpConsumer.getUrl());
		}
	}

	public WSRPConsumerPortlet getWSRPConsumerPortlet(
			long wsrpConsumerId, String portletHandle)
		throws PortalException {

		return wsrpConsumerPortletPersistence.findByW_P(
			wsrpConsumerId, portletHandle);
	}

	public WSRPConsumerPortlet getWSRPConsumerPortlet(
			String wsrpConsumerPortletUuid)
		throws PortalException {

		List<WSRPConsumerPortlet> wsrpConsumerPortlets =
			wsrpConsumerPortletPersistence.findByUuid(wsrpConsumerPortletUuid);

		if (wsrpConsumerPortlets.isEmpty()) {
			throw new NoSuchConsumerPortletException(
				"No WSRP consumer portlet exists with uuid " +
					wsrpConsumerPortletUuid);
		}

		return wsrpConsumerPortlets.get(0);
	}

	public List<WSRPConsumerPortlet> getWSRPConsumerPortlets(
		long wsrpConsumerId, int start, int end) {

		return wsrpConsumerPortletPersistence.findByWsrpConsumerId(
			wsrpConsumerId, start, end);
	}

	public int getWSRPConsumerPortletsCount(long wsrpConsumerId) {
		return wsrpConsumerPortletPersistence.countByWsrpConsumerId(
			wsrpConsumerId);
	}

	@Clusterable
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public void initFailedWSRPConsumerPortlets() {
		for (Map.Entry<Long, Tuple> entry :
				_failedWSRPConsumerPortlets.entrySet()) {

			long wsrpConsumerPortletId = entry.getKey();
			Tuple tuple = entry.getValue();

			try {
				long companyId = (Long)tuple.getObject(0);
				long wsrpConsumerId = (Long)tuple.getObject(1);
				String wsrpConsumerPortletUuid = (String)tuple.getObject(2);
				String name = (String)tuple.getObject(3);
				String portletHandle = (String)tuple.getObject(4);

				_failedWSRPConsumerPortlets.remove(wsrpConsumerPortletId);

				initWSRPConsumerPortlet(
					companyId, wsrpConsumerId, wsrpConsumerPortletId,
					wsrpConsumerPortletUuid, name, portletHandle);
			}
			catch (Exception e) {
				_log.error(
					"Unable to reinitialize WSRP consumer portlet " +
						wsrpConsumerPortletId,
					e);
			}
		}
	}

	@Clusterable
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public void initWSRPConsumerPortlet(
			long companyId, long wsrpConsumerId, long wsrpConsumerPortletId,
			String wsrpConsumerPortletUuid, String name, String portletHandle)
		throws PortalException {

		boolean initializationFailed = false;

		try {
			Portlet portlet = getPortlet(
				companyId, wsrpConsumerId, wsrpConsumerPortletUuid, name,
				portletHandle);

			if (!portlet.isActive()) {
				initializationFailed = true;
			}

			PortletLocalServiceUtil.deployRemotePortlet(
				portlet, _WSRP_CATEGORY);
		}
		catch (PortalException pe) {
			initializationFailed = true;

			throw pe;
		}
		catch (SystemException se) {
			initializationFailed = true;

			throw se;
		}
		catch (Exception e) {
			initializationFailed = true;

			throw new SystemException(e);
		}
		finally {
			if (initializationFailed) {
				Tuple tuple = new Tuple(
					companyId, wsrpConsumerId, wsrpConsumerPortletUuid, name,
					portletHandle);

				_failedWSRPConsumerPortlets.put(wsrpConsumerPortletId, tuple);
			}
		}
	}

	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public void initWSRPConsumerPortlets() {
		for (WSRPConsumerPortlet wsrpConsumerPortlet :
				wsrpConsumerPortletPersistence.findAll()) {

			try {
				initWSRPConsumerPortlet(
					wsrpConsumerPortlet.getCompanyId(),
					wsrpConsumerPortlet.getWsrpConsumerId(),
					wsrpConsumerPortlet.getWsrpConsumerPortletId(),
					wsrpConsumerPortlet.getUuid(),
					wsrpConsumerPortlet.getName(),
					wsrpConsumerPortlet.getPortletHandle());
			}
			catch (Exception e) {
				_log.error(
					"Unable to initialize WSRP consumer portlet " +
						wsrpConsumerPortlet.getUuid(),
					e);
			}
		}
	}

	public WSRPConsumerPortlet updateWSRPConsumerPortlet(
			long wsrpConsumerPortletId, String name)
		throws PortalException {

		validate(name);

		WSRPConsumerPortlet wsrpConsumerPortlet =
			wsrpConsumerPortletPersistence.findByPrimaryKey(
				wsrpConsumerPortletId);

		wsrpConsumerPortlet.setModifiedDate(new Date());
		wsrpConsumerPortlet.setName(name);

		wsrpConsumerPortletPersistence.update(wsrpConsumerPortlet);

		return wsrpConsumerPortlet;
	}

	protected void addPortletExtraInfo(
		Portlet portlet, PortletApp portletApp,
		PortletDescription portletDescription, String title) {

		MarkupType[] markupTypes = portletDescription.getMarkupTypes();

		for (MarkupType markupType : markupTypes) {
			Set<String> mimeTypePortletModes = new HashSet<>();

			for (String portletMode : markupType.getModes()) {
				portletMode = StringUtil.toLowerCase(portletMode);

				if (portletMode.startsWith("wsrp:")) {
					portletMode = portletMode.substring(5);
				}

				mimeTypePortletModes.add(portletMode);
			}

			portlet.getPortletModes().put(
				markupType.getMimeType(), mimeTypePortletModes);

			Set<String> mimeTypeWindowStates = new HashSet<>();

			for (String windowState : markupType.getWindowStates()) {
				windowState = StringUtil.toLowerCase(windowState);

				if (windowState.startsWith("wsrp:")) {
					windowState = windowState.substring(5);
				}

				mimeTypeWindowStates.add(windowState);
			}

			portlet.getWindowStates().put(
				markupType.getMimeType(), mimeTypeWindowStates);
		}

		String shortTitle = LocalizedStringUtil.getLocalizedStringValue(
			portletDescription.getShortTitle(), title);
		String keywords = StringUtil.merge(
			LocalizedStringUtil.getLocalizedStringValues(
				portletDescription.getKeywords()),
			StringPool.SPACE);
		String description = LocalizedStringUtil.getLocalizedStringValue(
			portletDescription.getShortTitle());

		PortletInfo portletInfo = new PortletInfo(
			title, shortTitle, keywords, description);

		portlet.setPortletInfo(portletInfo);

		portlet.setFriendlyURLMapperClass(
			ConsumerFriendlyURLMapper.class.getName());

		ParameterDescription[] parameterDescriptions =
			portletDescription.getNavigationalPublicValueDescriptions();

		if (parameterDescriptions != null) {
			for (ParameterDescription parameterDescription :
					parameterDescriptions) {

				QName[] qNames = parameterDescription.getNames();

				if (ArrayUtil.isEmpty(qNames)) {
					continue;
				}

				com.liferay.portal.kernel.xml.QName qName = getQName(qNames[0]);

				String identifier = parameterDescription.getIdentifier();

				portletApp.addPublicRenderParameter(identifier, qName);

				portlet.addPublicRenderParameter(
					portletApp.getPublicRenderParameter(identifier));
			}
		}

		QName[] handledEvents = portletDescription.getHandledEvents();

		if (handledEvents != null) {
			for (QName handledEvent : handledEvents) {
				portlet.addProcessingEvent(getQName(handledEvent));
			}
		}

		QName[] publishedEvents = portletDescription.getPublishedEvents();

		if (publishedEvents != null) {
			for (QName publishedEvent : publishedEvents) {
				portlet.addPublishingEvent(getQName(publishedEvent));
			}
		}

		MessageElement[] messageElements =
			ExtensionHelperUtil.getMessageElements(
				portletDescription.getExtensions());

		if (messageElements != null) {
			for (MessageElement messageElement : messageElements) {
				setExtension(portlet, messageElement);
			}
		}
	}

	protected ConsumerPortlet getConsumerPortletInstance(Portlet portlet)
		throws Exception {

		if (_consumerPortletClass == null) {
			ClassLoader classLoader = getClassLoader();

			_consumerPortletClass =
				(Class<ConsumerPortlet>)classLoader.loadClass(
					portlet.getPortletClass());
		}

		return _consumerPortletClass.newInstance();
	}

	protected Portlet getPortlet(
			long companyId, long wsrpConsumerId, String wsrpConsumerPortletUuid,
			String name, String portletHandle)
		throws Exception {

		Portlet portlet = _portletsPool.get(wsrpConsumerPortletUuid);

		if ((portlet != null) && portlet.isActive()) {
			return portlet;
		}

		String portletId = getPortletId(wsrpConsumerPortletUuid);

		portlet = PortletLocalServiceUtil.clonePortlet(_CONSUMER_PORTLET_ID);

		portlet.setCompanyId(companyId);
		portlet.setPortletId(portletId);

		PortletApp portletApp = PortletLocalServiceUtil.getPortletApp(
			ClpSerializer.getServletContextName());

		portlet.setPortletApp(portletApp);

		portlet.setPortletName(portletId);
		portlet.setDisplayName(portletId);

		Map<String, String> initParams = portlet.getInitParams();

		initParams.put(
			InvokerPortlet.INIT_INVOKER_PORTLET_NAME, _CONSUMER_PORTLET_NAME);

		WSRPConsumer wsrpConsumer = wsrpConsumerPersistence.findByPrimaryKey(
			wsrpConsumerId);

		PortletDescription portletDescription = null;

		try {
			WSRPConsumerManager wsrpConsumerManager =
				WSRPConsumerManagerFactory.getWSRPConsumerManager(wsrpConsumer);

			portletDescription = wsrpConsumerManager.getPortletDescription(
				portletHandle);
		}
		catch (Exception e) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to connect to WSRP producer for portlet " +
						wsrpConsumerPortletUuid,
					e);
			}
		}

		if (portletDescription != null) {
			addPortletExtraInfo(portlet, portletApp, portletDescription, name);

			portlet.setActive(true);
		}
		else {
			PortletInfo portletInfo = new PortletInfo(
				name, name, StringPool.BLANK, StringPool.BLANK);

			portlet.setPortletInfo(portletInfo);
		}

		_portletsPool.put(wsrpConsumerPortletUuid, portlet);

		PortletBag portletBag = PortletBagPool.get(_CONSUMER_PORTLET_ID);

		portletBag = (PortletBag)portletBag.clone();

		portletBag.setPortletName(portletId);

		ConsumerPortlet consumerPortletInstance = getConsumerPortletInstance(
			portlet);

		portletBag.setPortletInstance(consumerPortletInstance);

		PortletBagPool.put(portletId, portletBag);

		return portlet;
	}

	protected String getPortletId(String wsrpConsumerPortletUuid) {
		String portletId = ConsumerPortlet.PORTLET_NAME_PREFIX.concat(
			wsrpConsumerPortletUuid);

		portletId = PortalUtil.getJsSafePortletId(
			PortalUUIDUtil.toJsSafeUuid(portletId));

		return portletId;
	}

	protected String getProxyURL(String url) {
		return "/proxy?url=" + HttpUtil.encodeURL(url);
	}

	protected com.liferay.portal.kernel.xml.QName getQName(QName qName) {
		String localPart = qName.getLocalPart();
		String prefix = qName.getPrefix();
		String namespaceURI = qName.getNamespaceURI();

		Namespace namespace = SAXReaderUtil.createNamespace(
			prefix, namespaceURI);

		return SAXReaderUtil.createQName(localPart, namespace);
	}

	protected void setExtension(
		Portlet portlet, MessageElement messageElement) {

		String name = ExtensionHelperUtil.getNameAttribute(messageElement);
		String value = messageElement.getValue();

		if (Validator.isNull(name)) {
			if (_log.isInfoEnabled()) {
				_log.info(
					"Message element " + messageElement.toString() +
						" has a null name");
			}

			return;
		}

		if (name.equals("css-class-wrapper")) {
			portlet.setCssClassWrapper(value);
		}
		else if (name.equals("footer-portal-css")) {
			portlet.getFooterPortalCss().add(getProxyURL(value));
		}
		else if (name.equals("footer-portal-javascript")) {
			portlet.getFooterPortalJavaScript().add(getProxyURL(value));
		}
		else if (name.equals("footer-portlet-css")) {
			portlet.getFooterPortletCss().add(getProxyURL(value));
		}
		else if (name.equals("footer-portlet-javascript")) {
			portlet.getFooterPortletJavaScript().add(getProxyURL(value));
		}
		else if (name.equals("header-portal-css")) {
			portlet.getHeaderPortalCss().add(getProxyURL(value));
		}
		else if (name.equals("header-portal-javascript")) {
			portlet.getHeaderPortalJavaScript().add(getProxyURL(value));
		}
		else if (name.equals("header-portlet-css")) {
			portlet.getHeaderPortletCss().add(getProxyURL(value));
		}
		else if (name.equals("header-portlet-javascript")) {
			portlet.getHeaderPortletJavaScript().add(getProxyURL(value));
		}
	}

	protected void validate(String name) throws PortalException {
		if (Validator.isNull(name)) {
			throw new WSRPConsumerPortletNameException();
		}
	}

	protected void validate(String name, String portletHandle)
		throws PortalException {

		validate(name);

		if (Validator.isNull(portletHandle)) {
			throw new WSRPConsumerPortletHandleException();
		}
	}

	private static final String _CONSUMER_PORTLET_ID = "2_WAR_wsrpportlet";

	private static final String _CONSUMER_PORTLET_NAME = "2";

	private static final String _WSRP_CATEGORY = "category.wsrp";

	private static Log _log = LogFactoryUtil.getLog(
		WSRPConsumerPortletLocalServiceImpl.class);

	private static Map<String, Portlet> _portletsPool =
		new ConcurrentHashMap<>();

	private Class<ConsumerPortlet> _consumerPortletClass;
	private Map<Long, Tuple> _failedWSRPConsumerPortlets =
		new ConcurrentHashMap<>();

}