/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.journal.friendly.url.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.friendly.url.model.FriendlyURLEntry;
import com.liferay.friendly.url.service.FriendlyURLEntryLocalService;
import com.liferay.journal.constants.JournalArticleConstants;
import com.liferay.journal.constants.JournalFolderConstants;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.journal.test.util.JournalTestUtil;
import com.liferay.portal.configuration.test.util.ConfigurationTemporarySwapper;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.util.FriendlyURLNormalizerUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.LocaleThreadLocal;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Ricardo Couso
 */
@RunWith(Arquillian.class)
public class JournalArticleFriendlyURLTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	@Before
	public void setUp() throws Exception {
		_group = GroupTestUtil.addGroup();
	}

	@Test
	public void testFriendlyURLAfterRemovingLastVersion() throws Exception {
		String frTitle = RandomTestUtil.randomString();

		String usTitle = RandomTestUtil.randomString();

		JournalArticle article = _addJournalArticleWithTitleMap(
			LocaleUtil.US,
			HashMapBuilder.put(
				LocaleUtil.US, usTitle
			).build());

		JournalArticle updatedArticle1 = _updateJournalArticleWithTitleMap(
			article,
			HashMapBuilder.put(
				LocaleUtil.FRANCE, frTitle
			).put(
				LocaleUtil.US, usTitle
			).build());

		JournalArticle updatedArticle2 = _updateJournalArticleWithTitleMap(
			updatedArticle1,
			HashMapBuilder.put(
				LocaleUtil.FRANCE, frTitle
			).put(
				LocaleUtil.SPAIN, RandomTestUtil.randomString()
			).put(
				LocaleUtil.US, usTitle
			).build());

		_journalArticleLocalService.deleteArticle(
			updatedArticle2, updatedArticle2.getUrlTitle(),
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), TestPropsValues.getUserId()));

		JournalArticle persistedArticle =
			_journalArticleLocalService.fetchLatestArticle(
				article.getResourcePrimKey());

		Map<Locale, String> friendlyURLMap =
			persistedArticle.getFriendlyURLMap();

		Assert.assertEquals(
			friendlyURLMap.toString(), 3, friendlyURLMap.size());
	}

	@Test
	public void testFriendlyURLAfterUpdate() throws Exception {
		JournalArticle article = _addJournalArticleWithTitleMap(
			LocaleUtil.US,
			_getLocalizedMap(
				RandomTestUtil.randomString(),
				new Locale[] {LocaleUtil.FRANCE, LocaleUtil.US}));

		Map<Locale, String> friendlyURLMap = article.getFriendlyURLMap();

		friendlyURLMap.put(
			LocaleUtil.US, friendlyURLMap.get(LocaleUtil.US) + "-edited");

		JournalArticle updatedArticle =
			_journalArticleLocalService.updateArticle(
				article.getUserId(), _group.getGroupId(), article.getFolderId(),
				article.getArticleId(), article.getVersion(),
				article.getTitleMap(), article.getDescriptionMap(),
				friendlyURLMap, article.getContent(),
				article.getDDMTemplateKey(), article.getLayoutUuid(), 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, true, 0, 0, 0, 0, 0, true,
				article.isIndexable(), article.isSmallImage(), 0,
				article.getSmallImageSource(), article.getSmallImageURL(), null,
				null, null,
				ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		Assert.assertEquals(friendlyURLMap, updatedArticle.getFriendlyURLMap());

		updatedArticle = _journalArticleLocalService.updateArticle(
			updatedArticle.getUserId(), updatedArticle.getGroupId(),
			updatedArticle.getFolderId(), updatedArticle.getArticleId(),
			updatedArticle.getVersion(), updatedArticle.getContent(),
			ServiceContextTestUtil.getServiceContext(_group.getGroupId()));

		Assert.assertEquals(friendlyURLMap, updatedArticle.getFriendlyURLMap());
	}

	@Test
	public void testFriendlyURLRedirectAfterMaxVersionExpiration()
		throws Exception {

		try (ConfigurationTemporarySwapper configurationTemporarySwapper =
				new ConfigurationTemporarySwapper(
					"com.liferay.journal.configuration." +
						"JournalServiceConfiguration",
					HashMapDictionaryBuilder.<String, Object>put(
						"journalArticleMaxVersionCount", 2
					).build())) {

			JournalArticle article = _addJournalArticleWithTitleMap(
				LocaleUtil.US,
				HashMapBuilder.put(
					LocaleUtil.US, RandomTestUtil.randomString()
				).build());

			String initialUrlTitle = article.getUrlTitle();

			JournalArticle updatedArticle = _updateFriendlyURL(
				article, LocaleUtil.US, RandomTestUtil.randomString());

			String updatedUrlTitle = updatedArticle.getUrlTitle();

			Assert.assertNotEquals(initialUrlTitle, updatedUrlTitle);

			_updateFriendlyURL(updatedArticle, LocaleUtil.US, updatedUrlTitle);

			JournalArticle expiredArticle =
				_journalArticleLocalService.fetchArticle(
					_group.getGroupId(), article.getArticleId(),
					article.getVersion());

			Assert.assertEquals(
				WorkflowConstants.STATUS_EXPIRED, expiredArticle.getStatus());

			FriendlyURLEntry friendlyURLEntry =
				_friendlyURLEntryLocalService.fetchFriendlyURLEntry(
					_group.getGroupId(), JournalArticle.class, initialUrlTitle);

			Assert.assertNotNull(friendlyURLEntry);
			Assert.assertEquals(
				article.getResourcePrimKey(), friendlyURLEntry.getClassPK());
		}
	}

	@Test
	public void testFriendlyURLRemovedAfterVersionDeletion() throws Exception {
		JournalArticle article = _addJournalArticleWithTitleMap(
			LocaleUtil.US,
			HashMapBuilder.put(
				LocaleUtil.US, RandomTestUtil.randomString()
			).build());

		String initialUrlTitle = article.getUrlTitle();

		JournalArticle updatedArticle = _updateFriendlyURL(
			article, LocaleUtil.US, RandomTestUtil.randomString());

		String updatedUrlTitle = updatedArticle.getUrlTitle();

		Assert.assertNotEquals(initialUrlTitle, updatedUrlTitle);

		Assert.assertNotNull(
			_friendlyURLEntryLocalService.fetchFriendlyURLEntry(
				_group.getGroupId(), JournalArticle.class, initialUrlTitle));

		_journalArticleLocalService.deleteArticle(
			_group.getGroupId(), article.getArticleId(), article.getVersion(),
			null,
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), TestPropsValues.getUserId()));

		Assert.assertNull(
			_friendlyURLEntryLocalService.fetchFriendlyURLEntry(
				_group.getGroupId(), JournalArticle.class, initialUrlTitle));

		FriendlyURLEntry friendlyURLEntry =
			_friendlyURLEntryLocalService.fetchFriendlyURLEntry(
				_group.getGroupId(), JournalArticle.class, updatedUrlTitle);

		Assert.assertNotNull(friendlyURLEntry);
		Assert.assertEquals(
			article.getResourcePrimKey(), friendlyURLEntry.getClassPK());
	}

	@Test
	public void testFriendlyURLRemovedForNondefaultLocaleAfterVersionDeletion()
		throws Exception {

		JournalArticle article = _addJournalArticleWithTitleMap(
			LocaleUtil.US,
			HashMapBuilder.put(
				LocaleUtil.FRANCE, RandomTestUtil.randomString()
			).put(
				LocaleUtil.US, RandomTestUtil.randomString()
			).build());

		Map<Locale, String> initialFriendlyURLMap = article.getFriendlyURLMap();

		String initialFrUrlTitle = initialFriendlyURLMap.get(LocaleUtil.FRANCE);
		String initialUsUrlTitle = initialFriendlyURLMap.get(LocaleUtil.US);

		_updateFriendlyURL(
			article, LocaleUtil.FRANCE, RandomTestUtil.randomString());

		Assert.assertNotNull(
			_friendlyURLEntryLocalService.fetchFriendlyURLEntry(
				_group.getGroupId(), JournalArticle.class, initialFrUrlTitle));

		_journalArticleLocalService.deleteArticle(
			_group.getGroupId(), article.getArticleId(), article.getVersion(),
			null,
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), TestPropsValues.getUserId()));

		Assert.assertNull(
			_friendlyURLEntryLocalService.fetchFriendlyURLEntry(
				_group.getGroupId(), JournalArticle.class, initialFrUrlTitle));
		Assert.assertNotNull(
			_friendlyURLEntryLocalService.fetchFriendlyURLEntry(
				_group.getGroupId(), JournalArticle.class, initialUsUrlTitle));
	}

	@Test
	public void testFriendlyURLRetainedWhenUrlTitleReused() throws Exception {
		JournalArticle article = _addJournalArticleWithTitleMap(
			LocaleUtil.US,
			HashMapBuilder.put(
				LocaleUtil.US, RandomTestUtil.randomString()
			).build());

		String urlTitle = article.getUrlTitle();

		JournalArticle updatedArticle = _updateFriendlyURL(
			article, LocaleUtil.US, urlTitle);

		Assert.assertEquals(urlTitle, updatedArticle.getUrlTitle());
		Assert.assertNotEquals(
			article.getVersion(), updatedArticle.getVersion());

		_journalArticleLocalService.deleteArticle(
			_group.getGroupId(), article.getArticleId(), article.getVersion(),
			null,
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), TestPropsValues.getUserId()));

		FriendlyURLEntry friendlyURLEntry =
			_friendlyURLEntryLocalService.fetchFriendlyURLEntry(
				_group.getGroupId(), JournalArticle.class, urlTitle);

		Assert.assertNotNull(friendlyURLEntry);
		Assert.assertEquals(
			article.getResourcePrimKey(), friendlyURLEntry.getClassPK());
	}

	@Test
	public void testFriendlyURLShouldNotHaveSiteLocaleNotEqualDefaultLocale()
		throws Exception {

		Locale originalSiteDefaultLocale =
			LocaleThreadLocal.getSiteDefaultLocale();

		try {
			LocaleThreadLocal.setSiteDefaultLocale(LocaleUtil.US);

			String title = RandomTestUtil.randomString();
			Locale[] locales = {LocaleUtil.SPAIN};

			Map<Locale, String> titleMap = _getLocalizedMap(title, locales);

			JournalArticle article = _addJournalArticleWithTitleMap(
				LocaleUtil.SPAIN, titleMap);

			Map<Locale, String> friendlyURLMap = article.getFriendlyURLMap();

			Assert.assertNull(friendlyURLMap.get(LocaleUtil.getSiteDefault()));
			Assert.assertEquals(
				friendlyURLMap.toString(), 1, friendlyURLMap.size());
		}
		finally {
			LocaleThreadLocal.setSiteDefaultLocale(originalSiteDefaultLocale);
		}
	}

	@Test
	public void testUniqueFriendlyURLAfterUpdate() throws Exception {
		String title1 = RandomTestUtil.randomString();
		Locale[] locales = {LocaleUtil.FRANCE, LocaleUtil.US};

		Map<Locale, String> titleMap1 = _getLocalizedMap(title1, locales);

		JournalArticle article = _addJournalArticleWithTitleMap(
			LocaleUtil.US, titleMap1);

		String title2 = RandomTestUtil.randomString();

		Map<Locale, String> titleMap2 = _getLocalizedMap(title2, locales);

		_addJournalArticleWithTitleMap(LocaleUtil.US, titleMap2);

		JournalArticle updatedArticle = _updateJournalArticleWithTitleMap(
			article, titleMap2);

		Map<Locale, String> friendlyURLMap = updatedArticle.getFriendlyURLMap();

		Assert.assertEquals(
			FriendlyURLNormalizerUtil.normalizeWithEncoding(title2 + "-1"),
			friendlyURLMap.get(LocaleUtil.US));
		Assert.assertEquals(
			FriendlyURLNormalizerUtil.normalizeWithEncoding(title2 + "-1"),
			friendlyURLMap.get(LocaleUtil.FRANCE));
	}

	@Test
	public void testUniqueFriendlyURLForDifferentTitles() throws Exception {
		String frTitle = RandomTestUtil.randomString();
		String usTitle = RandomTestUtil.randomString();

		JournalArticle article = _addJournalArticleWithTitleMap(
			LocaleUtil.US,
			HashMapBuilder.put(
				LocaleUtil.FRANCE, frTitle
			).put(
				LocaleUtil.US, usTitle
			).build());

		Map<Locale, String> friendlyURLMap = article.getFriendlyURLMap();

		Assert.assertEquals(
			FriendlyURLNormalizerUtil.normalizeWithEncoding(usTitle),
			friendlyURLMap.get(LocaleUtil.US));
		Assert.assertEquals(
			FriendlyURLNormalizerUtil.normalizeWithEncoding(frTitle),
			friendlyURLMap.get(LocaleUtil.FRANCE));
	}

	@Test
	public void testUniqueFriendlyURLForExistingArticle() throws Exception {
		String title = RandomTestUtil.randomString();

		Map<Locale, String> titleMap = _getLocalizedMap(
			title, new Locale[] {LocaleUtil.US});

		_addJournalArticleWithTitleMap(LocaleUtil.US, titleMap);

		JournalArticle article = _addJournalArticleWithTitleMap(
			LocaleUtil.US, titleMap);

		Map<Locale, String> friendlyURLMap = article.getFriendlyURLMap();

		Assert.assertEquals(
			FriendlyURLNormalizerUtil.normalizeWithEncoding(title + "-1"),
			friendlyURLMap.get(LocaleUtil.US));
	}

	@Test
	public void testUniqueFriendlyURLForSameTitles() throws Exception {
		String title = RandomTestUtil.randomString();
		Locale[] locales = {LocaleUtil.FRANCE, LocaleUtil.US};

		Map<Locale, String> titleMap = _getLocalizedMap(title, locales);

		JournalArticle article = _addJournalArticleWithTitleMap(
			LocaleUtil.US, titleMap);

		Map<Locale, String> friendlyURLMap = article.getFriendlyURLMap();

		Assert.assertEquals(
			FriendlyURLNormalizerUtil.normalizeWithEncoding(title),
			friendlyURLMap.get(LocaleUtil.US));
		Assert.assertEquals(
			FriendlyURLNormalizerUtil.normalizeWithEncoding(title),
			friendlyURLMap.get(LocaleUtil.FRANCE));
	}

	@Test
	public void testUniqueFriendlyURLForSameTitlesInNotDefaultLocaleWithExistingArticle()
		throws Exception {

		String frTitle = RandomTestUtil.randomString();

		_addJournalArticleWithTitleMap(
			LocaleUtil.US,
			HashMapBuilder.put(
				LocaleUtil.FRANCE, frTitle
			).put(
				LocaleUtil.US, RandomTestUtil.randomString()
			).build());

		JournalArticle article = _addJournalArticleWithTitleMap(
			LocaleUtil.US,
			HashMapBuilder.put(
				LocaleUtil.US, frTitle
			).build());

		Map<Locale, String> friendlyURLMap = article.getFriendlyURLMap();

		Assert.assertEquals(
			FriendlyURLNormalizerUtil.normalizeWithEncoding(frTitle + "-1"),
			friendlyURLMap.get(LocaleUtil.US));
	}

	@Test
	public void testUniqueFriendlyURLForSameTitlesWithExistingArticle()
		throws Exception {

		Locale[] locales = {LocaleUtil.FRANCE, LocaleUtil.US};
		String title = RandomTestUtil.randomString();

		Map<Locale, String> titleMap = _getLocalizedMap(title, locales);

		_addJournalArticleWithTitleMap(LocaleUtil.US, titleMap);

		JournalArticle article = _addJournalArticleWithTitleMap(
			LocaleUtil.US, titleMap);

		Map<Locale, String> friendlyURLMap = article.getFriendlyURLMap();

		Assert.assertEquals(
			FriendlyURLNormalizerUtil.normalizeWithEncoding(title + "-1"),
			friendlyURLMap.get(LocaleUtil.US));
		Assert.assertEquals(
			FriendlyURLNormalizerUtil.normalizeWithEncoding(title + "-1"),
			friendlyURLMap.get(LocaleUtil.FRANCE));
	}

	private JournalArticle _addJournalArticleWithTitleMap(
			Locale defaultLocale, Map<Locale, String> titleMap)
		throws Exception {

		return JournalTestUtil.addArticle(
			_group.getGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			JournalArticleConstants.CLASS_NAME_ID_DEFAULT, titleMap, titleMap,
			titleMap, defaultLocale, false, true,
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), TestPropsValues.getUserId()));
	}

	private Map<Locale, String> _getLocalizedMap(
		String value, Locale[] locales) {

		Map<Locale, String> valuesMap = new HashMap<>();

		for (Locale locale : locales) {
			valuesMap.put(locale, value);
		}

		return valuesMap;
	}

	private JournalArticle _updateFriendlyURL(
			JournalArticle article, Locale locale, String urlTitle)
		throws Exception {

		Map<Locale, String> friendlyURLMap = HashMapBuilder.putAll(
			article.getFriendlyURLMap()
		).put(
			locale, urlTitle
		).build();

		return _journalArticleLocalService.updateArticle(
			article.getUserId(), _group.getGroupId(), article.getFolderId(),
			article.getArticleId(), article.getVersion(), article.getTitleMap(),
			article.getDescriptionMap(), friendlyURLMap, article.getContent(),
			article.getDDMTemplateKey(), article.getLayoutUuid(), 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, true, 0, 0, 0, 0, 0, true, article.isIndexable(),
			article.isSmallImage(), 0, article.getSmallImageSource(),
			article.getSmallImageURL(), null, null, null,
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), TestPropsValues.getUserId()));
	}

	private JournalArticle _updateJournalArticleWithTitleMap(
			JournalArticle article, Map<Locale, String> titleMap)
		throws Exception {

		return JournalTestUtil.updateArticle(
			article, titleMap, article.getContent(), false, true,
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), TestPropsValues.getUserId()));
	}

	@Inject
	private FriendlyURLEntryLocalService _friendlyURLEntryLocalService;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private JournalArticleLocalService _journalArticleLocalService;

}