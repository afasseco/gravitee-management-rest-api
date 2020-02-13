/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.rest.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.ThemeRepository;
import io.gravitee.repository.management.model.Theme;
import io.gravitee.rest.api.model.theme.*;
import io.gravitee.rest.api.service.common.GraviteeContext;
import io.gravitee.rest.api.service.exceptions.ThemeNotFoundException;
import io.gravitee.rest.api.service.impl.ThemeServiceImpl;
import io.gravitee.rest.api.service.impl.ThemeServiceImpl.ThemeDefinitionMapper;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

import static io.gravitee.repository.management.model.Audit.AuditProperties.THEME;
import static io.gravitee.repository.management.model.ThemeReferenceType.ENVIRONMENT;
import static java.util.Collections.singleton;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Guillaume CUSNIEUX (guillaume.cusnieux at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ThemeServiceTest {

    private static final String THEME_ID = "id-theme";

    @InjectMocks
    private ThemeService themeService = new ThemeServiceImpl();

    @Mock
    private ThemeRepository themeRepository;

    @Mock
    private AuditService auditService;

    @Test
    public void shouldFindById() throws TechnicalException, JsonProcessingException {
        ThemeDefinitionMapper definitionMapper = new ThemeDefinitionMapper();
        ThemeDefinition themeDefinition = new ThemeDefinition();
        themeDefinition.setData(Collections.EMPTY_LIST);
        String definition = definitionMapper.writeValueAsString(themeDefinition);

        final Theme theme = mock(Theme.class);
        when(theme.getId()).thenReturn(THEME_ID);
        when(theme.getName()).thenReturn("NAME");
        when(theme.getDefinition()).thenReturn(definition);
        when(theme.getReferenceId()).thenReturn("DEFAULT");
        when(theme.getCreatedAt()).thenReturn(new Date(1));
        when(theme.getUpdatedAt()).thenReturn(new Date(2));
        when(themeRepository.findById(THEME_ID)).thenReturn(of(theme));

        final ThemeEntity themeEntity = themeService.findById(THEME_ID);
        assertEquals(THEME_ID, themeEntity.getId());
        assertEquals("NAME", themeEntity.getName());
        assertEquals(definition, definitionMapper.writeValueAsString(themeEntity.getDefinition()));
        assertEquals(new Date(1), themeEntity.getCreatedAt());
        assertEquals(new Date(2), themeEntity.getUpdatedAt());
    }

    @Test(expected = ThemeNotFoundException.class)
    public void shouldNotFindById() throws TechnicalException {
        when(themeRepository.findById(THEME_ID)).thenReturn(empty());
        themeService.findById(THEME_ID);
    }

    @Test
    public void shouldFindAll() throws TechnicalException, JsonProcessingException {
        ThemeDefinitionMapper definitionMapper = new ThemeDefinitionMapper();
        ThemeDefinition themeDefinition = new ThemeDefinition();
        themeDefinition.setData(Collections.EMPTY_LIST);
        String definition = definitionMapper.writeValueAsString(themeDefinition);
        final Theme theme = mock(Theme.class);
        when(theme.getId()).thenReturn(THEME_ID);
        when(theme.getName()).thenReturn("NAME");
        when(theme.getDefinition()).thenReturn(definition);
        when(theme.getCreatedAt()).thenReturn(new Date(1));
        when(theme.getUpdatedAt()).thenReturn(new Date(2));
        when(themeRepository.findByReference(GraviteeContext.getCurrentEnvironment(), ENVIRONMENT.name())).thenReturn(singleton(theme));

        final Set<ThemeEntity> themes = themeService.findAll();
        final ThemeEntity themeEntity = themes.iterator().next();
        assertEquals(THEME_ID, themeEntity.getId());
        assertEquals("NAME", themeEntity.getName());
        assertEquals(definition, definitionMapper.writeValueAsString(themeEntity.getDefinition()));
        assertEquals(new Date(1), themeEntity.getCreatedAt());
        assertEquals(new Date(2), themeEntity.getUpdatedAt());
    }

    @Test
    public void shouldCreate() throws TechnicalException, IOException {
        ThemeDefinitionMapper definitionMapper = new ThemeDefinitionMapper();
        ThemeDefinition themeDefinition = new ThemeDefinition();
        themeDefinition.setData(Collections.EMPTY_LIST);
        String definition = definitionMapper.writeValueAsString(themeDefinition);
        final NewThemeEntity newThemeEntity = new NewThemeEntity();
        newThemeEntity.setName("NAME");
        newThemeEntity.setDefinition(themeDefinition);

        final Theme createdTheme = new Theme();
        createdTheme.setId(THEME_ID);
        createdTheme.setName("NAME");
        createdTheme.setDefinition(definition);
        createdTheme.setCreatedAt(new Date());
        createdTheme.setUpdatedAt(new Date());
        when(themeRepository.create(any())).thenReturn(createdTheme);

        final ThemeEntity themeEntity = themeService.create(newThemeEntity);

        assertNotNull(themeEntity.getId());
        assertEquals("NAME", themeEntity.getName());
        assertNotNull(themeEntity.getDefinition());
        assertEquals(0, themeEntity.getDefinition().getData().size());
        assertNotNull(themeEntity.getCreatedAt());
        assertNotNull(themeEntity.getUpdatedAt());

        final Theme theme = new Theme();
        theme.setName("NAME");
        theme.setDefinition(definition);
        theme.setReferenceId("REF_ID");
        theme.setReferenceType(ENVIRONMENT.name());

        verify(themeRepository, times(1)).create(argThat(argument ->
        {
            return "NAME".equals(argument.getName()) &&
                    argument.getDefinition() != null &&
                    "DEFAULT".equals(argument.getReferenceId()) &&
                    ENVIRONMENT.name().equals(argument.getReferenceType()) &&
                    !argument.getId().isEmpty() &&
                    argument.getCreatedAt() != null &&
                    argument.getUpdatedAt() != null;
        }));
        verify(auditService, times(1)).createPortalAuditLog(
                eq(ImmutableMap.of(THEME, THEME_ID)),
                eq(Theme.AuditEvent.THEME_CREATED),
                any(Date.class),
                isNull(),
                any());
    }

    @Test
    public void shouldUpdate() throws TechnicalException, JsonProcessingException {
        ThemeDefinitionMapper definitionMapper = new ThemeDefinitionMapper();
        ThemeDefinition themeDefinition = new ThemeDefinition();
        themeDefinition.setData(Collections.EMPTY_LIST);
        String definition = definitionMapper.writeValueAsString(themeDefinition);

        final UpdateThemeEntity updateThemeEntity = new UpdateThemeEntity();
        updateThemeEntity.setId(THEME_ID);
        updateThemeEntity.setName("NAME");
        updateThemeEntity.setDefinition(themeDefinition);

        final Theme updatedTheme = new Theme();
        updatedTheme.setId(THEME_ID);
        updatedTheme.setName("NAME");
        updatedTheme.setDefinition(definition);
        updatedTheme.setCreatedAt(new Date());
        updatedTheme.setUpdatedAt(new Date());
        when(themeRepository.update(any())).thenReturn(updatedTheme);
        when(themeRepository.findById(THEME_ID)).thenReturn(of(updatedTheme));

        final ThemeEntity themeEntity = themeService.update(updateThemeEntity);

        assertNotNull(themeEntity.getId());
        assertEquals("NAME", themeEntity.getName());
        assertEquals(definition, definitionMapper.writeValueAsString(themeEntity.getDefinition()));
        assertNotNull(themeEntity.getCreatedAt());
        assertNotNull(themeEntity.getUpdatedAt());


        final Theme theme = new Theme();
        theme.setName("NAME");
        theme.setDefinition(definition);
        theme.setReferenceId("REF_ID");
        theme.setReferenceType(ENVIRONMENT.name());

        verify(themeRepository, times(1)).update(argThat(argument ->
                "NAME".equals(argument.getName()) &&
                        argument.getDefinition() != null &&
                        "DEFAULT".equals(argument.getReferenceId()) &&
                        ENVIRONMENT.name().equals(argument.getReferenceType()) &&
                        THEME_ID.equals(argument.getId()) &&
                        argument.getUpdatedAt() != null));

        verify(auditService, times(1)).createPortalAuditLog(
                eq(ImmutableMap.of(THEME, THEME_ID)),
                eq(Theme.AuditEvent.THEME_UPDATED),
                any(Date.class),
                any(),
                any());
    }

    @Test(expected = ThemeNotFoundException.class)
    public void shouldNotUpdate() throws TechnicalException {
        final UpdateThemeEntity updateThemeEntity = new UpdateThemeEntity();
        updateThemeEntity.setId(THEME_ID);

        when(themeRepository.findById(THEME_ID)).thenReturn(empty());

        themeService.update(updateThemeEntity);
    }

    @Test
    public void shouldDelete() throws TechnicalException {
        final Theme theme = mock(Theme.class);
        when(themeRepository.findById(THEME_ID)).thenReturn(of(theme));

        themeService.delete(THEME_ID);

        verify(themeRepository, times(1)).delete(THEME_ID);
        verify(auditService, times(1)).createPortalAuditLog(
                eq(ImmutableMap.of(THEME, THEME_ID)),
                eq(Theme.AuditEvent.THEME_DELETED),
                any(Date.class),
                isNull(),
                eq(theme));
    }

    @Test
    public void shouldLoadDefaultThemeDefinition() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String definition = IOUtils.toString(this.getClass().getResourceAsStream(ThemeServiceImpl.DEFAULT_THEME_PATH), Charset.defaultCharset());
        ThemeDefinition themeDefinition = mapper.readValue(definition, ThemeDefinition.class);
        assertNotNull(themeDefinition);
        assertNotNull(themeDefinition.getData());
        assertEquals(themeDefinition.getData().size(), 36);
    }

    @Test
    public void shouldMergeThemeDefinition() throws IOException {
        ThemeDefinitionMapper mapper = new ThemeDefinitionMapper();
        String def = IOUtils.toString(this.getClass().getResourceAsStream("/themes/base-definition.json"), Charset.defaultCharset());
        ThemeDefinition baseDefinition = mapper.readValue(def, ThemeDefinition.class);
        String customDef = IOUtils.toString(this.getClass().getResourceAsStream("/themes/custom-definition.json"), Charset.defaultCharset());
        ThemeDefinition customDefinition = mapper.readValue(customDef, ThemeDefinition.class);
        assertEquals(customDefinition.getData().size(), 34);
        assertNull(mapper.getThemeComponentDefinition(baseDefinition, "gv-pagination"));
        assertNotNull(mapper.getThemeComponentDefinition(customDefinition, "gv-pagination"));
        assertEquals(mapper.getThemeComponentDefinition(baseDefinition, "gv-plans").getCss().size(), 5);
        assertEquals(mapper.getThemeComponentDefinition(customDefinition, "gv-plans").getCss().size(), 4);
        assertEquals(mapper.getThemeComponentDefinition(baseDefinition, "gv-popover").getCss().size(), 2);
        assertEquals(mapper.getThemeComponentDefinition(customDefinition, "gv-popover").getCss().size(), 3);
        ThemeCssDefinition gvThemeColor = mapper.getThemeCssDefinition(baseDefinition, "gv-theme", "--gv-theme-color");
        assertNull(gvThemeColor.getDefaultValue());
        assertEquals(gvThemeColor.getType(), ThemeCssType.COLOR);
        assertEquals(gvThemeColor.getValue(), "#009B5B");
        ThemeCssDefinition gvButtonFz = mapper.getThemeCssDefinition(baseDefinition, "gv-button", "--gv-button--fz");
        assertNull(gvButtonFz.getDefaultValue());
        assertEquals(gvButtonFz.getType(), ThemeCssType.LENGTH);
        assertEquals(gvButtonFz.getValue(), "var(--gv-theme-font-size-m, 14px)");
        assertEquals(gvButtonFz.getDescription(), "Font size");

        ThemeDefinition mergedDefinition = mapper.merge(def, customDef);

        assertEquals(mergedDefinition.getData().size(), 35);
        assertNull(mapper.getThemeComponentDefinition(mergedDefinition, "gv-pagination"));
        assertEquals(mapper.getThemeComponentDefinition(mergedDefinition, "gv-plans").getCss().size(), 5);
        assertEquals(mapper.getThemeComponentDefinition(mergedDefinition, "gv-popover").getCss().size(), 2);
        ThemeCssDefinition gvThemeColorMerged = mapper.getThemeCssDefinition(mergedDefinition, "gv-theme", "--gv-theme-color");
        assertNull(gvThemeColorMerged.getDefaultValue());
        assertEquals(gvThemeColorMerged.getType(), ThemeCssType.COLOR);
        assertEquals(gvThemeColorMerged.getValue(), "#FAFAFA");
        ThemeCssDefinition gvButtonFzMerged = mapper.getThemeCssDefinition(mergedDefinition, "gv-button", "--gv-button--fz");
        assertNull(gvButtonFzMerged.getDefaultValue());
        assertEquals(gvButtonFzMerged.getType(), ThemeCssType.LENGTH);
        assertEquals(gvButtonFzMerged.getValue(), "200px");
        assertEquals(gvButtonFzMerged.getDescription(), "Font size");
    }

}
