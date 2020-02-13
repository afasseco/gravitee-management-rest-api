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
package io.gravitee.rest.api.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.common.utils.UUID;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.ThemeRepository;
import io.gravitee.repository.management.model.RatingReferenceType;
import io.gravitee.repository.management.model.Theme;
import io.gravitee.repository.management.model.ThemeReferenceType;
import io.gravitee.rest.api.model.*;
import io.gravitee.rest.api.model.theme.*;
import io.gravitee.rest.api.service.*;
import io.gravitee.rest.api.service.common.GraviteeContext;
import io.gravitee.rest.api.service.exceptions.*;
import io.gravitee.rest.api.service.exceptions.ThemeNotFoundException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.activation.MimetypesFileTypeMap;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static io.gravitee.repository.management.model.Audit.AuditProperties.THEME;
import static io.gravitee.repository.management.model.Theme.AuditEvent.*;
import static io.gravitee.repository.management.model.Theme.AuditEvent.THEME_CREATED;
import static io.gravitee.repository.management.model.Theme.AuditEvent.THEME_DELETED;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.stream.Collectors.toList;

/**
 * @author Guillaume CUSNIEUX (azize at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ThemeServiceImpl extends AbstractService implements ThemeService {

    public static final String DEFAULT_THEME_PATH = "/themes/default/definition.json";
    private final Logger LOGGER = LoggerFactory.getLogger(ThemeServiceImpl.class);

    @Autowired
    private ThemeRepository themeRepository;

    @Autowired
    private AuditService auditService;

    @Override
    public Set<ThemeEntity> findAll() {
        try {
            LOGGER.debug("Find all themes by reference: " + GraviteeContext.getCurrentEnvironment());
            return themeRepository
                    .findByReference(GraviteeContext.getCurrentEnvironment(), ThemeReferenceType.ENVIRONMENT.name())
                    .stream()
                    .map(this::convert).collect(Collectors.toSet());
        } catch (Exception ex) {
            LOGGER.error("An error occurs while trying to find all themes", ex);
            throw new TechnicalManagementException("An error occurs while trying to find all themes", ex);
        }
    }

    @Override
    public ThemeEntity findById(String themeId) {
        return convert(this.findByIdWithoutConvert(themeId));
    }

    private Theme findByIdWithoutConvert(String themeId) {
        try {
            LOGGER.debug("Find theme by ID: {}", themeId);
            Optional<Theme> optTheme = themeRepository.findById(themeId);

            if (!optTheme.isPresent()) {
                throw new ThemeNotFoundException(themeId);
            }

            Theme theme = optTheme.get();
            if (!theme.getReferenceId().equals(GraviteeContext.getCurrentEnvironment())) {
                LOGGER.warn("Theme is not in current environment " +
                        GraviteeContext.getCurrentEnvironment() +
                        " actual:" + theme.getReferenceId());
                throw new ThemeNotFoundException(themeId);
            }
            return theme;
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find theme by ID", ex);
            throw new TechnicalManagementException("An error occurs while trying to find theme by ID", ex);
        }
    }

    @Override
    public ThemeEntity create(final NewThemeEntity themeEntity) {
        // First we prevent the duplicate name
        try {
            final Optional<ThemeEntity> optionalTheme = findAll().stream()
                    .filter(theme -> theme.getName().equals(themeEntity.getName()))
                    .findAny();

            if (optionalTheme.isPresent()) {
                throw new DuplicateThemeNameException(optionalTheme.get().getName());
            }


            Theme theme = themeRepository.create(convert(themeEntity));

            auditService.createPortalAuditLog(
                    Collections.singletonMap(THEME, theme.getId()),
                    THEME_CREATED,
                    theme.getCreatedAt(),
                    null,
                    theme);

            return convert(theme);

        } catch (Exception ex) {
            final String error = "An error occurred while trying to create theme " + themeEntity;
            LOGGER.error(error, ex);
            throw new TechnicalManagementException(error, ex);
        }
    }

    @Override
    public ThemeEntity update(final UpdateThemeEntity updateThemeEntity) {
        try {
            final Optional<Theme> themeOptional = themeRepository.findById(updateThemeEntity.getId());
            if (themeOptional.isPresent()) {

                ThemeDefinitionMapper definitionMapper = new ThemeDefinitionMapper();

                final Theme theme = new Theme(themeOptional.get());

                theme.setEnabled(updateThemeEntity.isEnabled());
                final Date now = new Date();
                theme.setUpdatedAt(now);
                theme.setReferenceType(ThemeReferenceType.ENVIRONMENT.name());
                theme.setReferenceId(GraviteeContext.getCurrentEnvironment());

                theme.setName(updateThemeEntity.getName());
                theme.setDefinition(definitionMapper.writeValueAsString(updateThemeEntity.getDefinition()));
                theme.setLogo(updateThemeEntity.getLogo());
                theme.setLoader(updateThemeEntity.getLoader());
                theme.setBackgroundImage(updateThemeEntity.getBackgroundImage());
                theme.setOptionalLogo(updateThemeEntity.getOptionalLogo());

                final ThemeEntity savedTheme = convert(themeRepository.update(theme));
                auditService.createPortalAuditLog(
                        Collections.singletonMap(THEME, theme.getId()),
                        THEME_UPDATED,
                        new Date(),
                        themeOptional.get(),
                        theme);
                return savedTheme;
            } else {
                throw new ThemeNotFoundException(updateThemeEntity.getId());
            }
        } catch (TechnicalException | JsonProcessingException ex) {
            final String error = "An error occurred while trying to update theme " + updateThemeEntity;
            LOGGER.error(error, ex);
            throw new TechnicalManagementException(error, ex);
        }
    }

    @Override
    public void delete(String themeId) {
        try {
            Optional<Theme> themeOptional = themeRepository.findById(themeId);
            if (themeOptional.isPresent()) {
                themeRepository.delete(themeId);
                auditService.createPortalAuditLog(
                        Collections.singletonMap(THEME, themeId),
                        THEME_DELETED,
                        new Date(),
                        null,
                        themeOptional.get());
            }
        } catch (TechnicalException ex) {
            final String error = "An error occurs while trying to delete theme " + themeId;
            LOGGER.error(error, ex);
            throw new TechnicalManagementException(error, ex);
        }
    }

    @Override
    public ThemeEntity findEnabled() {
        try {
            LOGGER.debug("Find all themes by reference type");
            Optional<Theme> themeEnabled = themeRepository.findByReference(GraviteeContext.getCurrentEnvironment(), ThemeReferenceType.ENVIRONMENT.name())
                    .stream()
                    .filter(theme -> theme.isEnabled())
                    .findFirst();

            if (themeEnabled.isPresent()) {
                return convert(themeEnabled.get());
            }

            return null;

        } catch (TechnicalException ex) {
            final String error = "An error occurs while trying to find all themes by reference type";
            LOGGER.error(error, ex);
            throw new TechnicalManagementException(error, ex);
        }
    }

    @Override
    public void updateDefaultTheme() {
        try {
            final Set<Theme> themes = themeRepository
                    .findByReference(GraviteeContext.getCurrentEnvironment(), ThemeReferenceType.ENVIRONMENT.name());

            String defaultDefinition = this.getDefaultDefinition();
            ThemeDefinitionMapper definitionMapper = new ThemeDefinitionMapper();
            if (themes == null || themes.isEmpty()) {

                NewThemeEntity theme = new NewThemeEntity();
                theme.setEnabled(false);
                theme.setName("Default");
                theme.setDefinition(definitionMapper.readDefinition(defaultDefinition));
                theme.setLogo(this.getDefaultLogo());
                theme.setOptionalLogo(this.getDefaultLogoLight());
                theme.setLoader(this.getDefaultLoader());
                theme.setBackgroundImage(this.getDefaultBackgroundImage());
                this.create(theme);
            } else {
                themes.forEach(theme -> {
                    try {
                        ThemeDefinition mergeDefinition = definitionMapper.merge(defaultDefinition, definitionMapper.writeValueAsString(theme.getDefinition()));
                        Theme themeUpdate = new Theme(theme);
                        themeUpdate.setDefinition(definitionMapper.writeValueAsString(mergeDefinition));
                        theme.setUpdatedAt(new Date());
                        this.themeRepository.update(themeUpdate);
                        auditService.createPortalAuditLog(
                                Collections.singletonMap(THEME, theme.getId()),
                                THEME_UPDATED,
                                new Date(),
                                theme,
                                themeUpdate);
                    } catch (IOException ex) {
                        final String error = "Error while trying to merge default theme from the definition path: "
                                + DEFAULT_THEME_PATH
                                + " with theme "
                                + theme.toString();
                        LOGGER.error(error, ex);
                    } catch (TechnicalException ex) {
                        final String error = "Error while trying to update theme after merge with default" + theme.toString();
                        LOGGER.error(error, ex);
                    }
                });
            }
        } catch (IOException ex) {
            final String error = "Error while trying to create default theme from the definition path: "
                    + DEFAULT_THEME_PATH;
            LOGGER.error(error, ex);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find all themes", ex);
        }
    }

    protected String getDefaultDefinition() {
        try {
            return IOUtils.toString(this.getClass().getResourceAsStream(DEFAULT_THEME_PATH), defaultCharset());
        } catch (IOException ex) {
            final String error = "Error while trying to create a theme from the definition path: " + DEFAULT_THEME_PATH;
            LOGGER.error(error, ex);
            throw new TechnicalManagementException(error, ex);
        }
    }

    private String getDefaultLogo() {
        return getImage("logo.png");
    }

    private String getDefaultLogoLight() {
        return getImage("logo-light.png");
    }

    private String getDefaultLoader() {
        return getImage("loader.gif");
    }

    private String getDefaultBackgroundImage() {
        return getImage("background.jpg");
    }

    private String getImage(String filename) {
        String filepath = "/themes/default/" + filename;
        try {
            byte[] image = IOUtils.toByteArray(this.getClass().getResourceAsStream(filepath));
            MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();
            return "data:" + fileTypeMap.getContentType(filename) + ";base64," + Base64.getEncoder().encodeToString(image);
        } catch (IOException ex) {
            final String error = "Error while trying to load image from: " + filepath;
            LOGGER.error(error, ex);
            return null;
        }
    }

    @Override
    public ThemeEntity resetToDefaultTheme(String themeId) {
        try {
            LOGGER.debug("Reset to default theme by ID: {}", themeId);


            UpdateThemeEntity updateThemeEntity = new UpdateThemeEntity();
            updateThemeEntity.setId(themeId);
            ThemeDefinitionMapper themeDefinitionMapper = new ThemeDefinitionMapper();
            updateThemeEntity.setDefinition(themeDefinitionMapper.readDefinition(getDefaultDefinition()));
            updateThemeEntity.setLogo(this.getDefaultLogo());
            updateThemeEntity.setOptionalLogo(this.getDefaultLogoLight());
            updateThemeEntity.setLoader(this.getDefaultLoader());
            updateThemeEntity.setBackgroundImage(this.getDefaultBackgroundImage());

            auditService.createPortalAuditLog(
                    Collections.singletonMap(THEME, themeId),
                    THEME_RESET,
                    new Date(),
                    null,
                    updateThemeEntity);

            return this.update(updateThemeEntity);
        } catch (IOException ex) {
            final String error = "Error while trying to reset a default theme";
            LOGGER.error(error, ex);
            throw new TechnicalManagementException(error, ex);
        }
    }

    @Override
    public PictureEntity getLogo(String themeId) {
        Theme theme = findByIdWithoutConvert(themeId);
        String logo = theme.getLogo();
        if (logo != null) {
            try {
                return convertToPicture(logo);
            } catch (Exception ex) {
                LOGGER.warn("Unable to get logo picture theme for id[{}]", themeId);
            }
        }
        return null;
    }

    @Override
    public PictureEntity getOptionalLogo(String themeId) {
        Theme theme = findByIdWithoutConvert(themeId);
        String logoLight = theme.getOptionalLogo();
        if (logoLight != null) {
            try {
                return convertToPicture(logoLight);
            } catch (Exception ex) {
                LOGGER.warn("Unable to get logo light picture theme for id[{}]", themeId);
            }
        }
        return null;
    }

    @Override
    public PictureEntity getLoader(String themeId) {
        Theme theme = findByIdWithoutConvert(themeId);
        String loader = theme.getLoader();
        if (loader != null) {
            try {
                return convertToPicture(loader);
            } catch (Exception ex) {
                LOGGER.warn("Unable to get loader picture theme for id[{}]", themeId);
            }
        }
        return null;
    }

    @Override
    public PictureEntity getBackgroundImage(String themeId) {
        Theme theme = findByIdWithoutConvert(themeId);
        String background = theme.getBackgroundImage();
        if (background != null) {
            try {
                return convertToPicture(background);
            } catch (Exception ex) {
                LOGGER.warn("Unable to get background picture theme for id[{}]", themeId);
            }
        }
        return null;
    }

    private PictureEntity convertToPicture(String picture) {
        if (picture.matches("^(http|https)://.*$")) {
            return new UrlPictureEntity(picture);
        } else {
            InlinePictureEntity imageEntity = new InlinePictureEntity();
            String[] parts = picture.split(";", 2);
            imageEntity.setType(parts[0].split(":")[1]);
            String base64Content = picture.split(",", 2)[1];
            imageEntity.setContent(DatatypeConverter.parseBase64Binary(base64Content));
            return imageEntity;
        }
    }

    private Theme convert(NewThemeEntity themeEntity) throws JsonProcessingException {
        ThemeDefinitionMapper definitionMapper = new ThemeDefinitionMapper();
        final Date now = new Date();
        final Theme theme = new Theme();
        theme.setId(io.gravitee.common.utils.UUID.toString(UUID.random()));
        theme.setCreatedAt(now);
        theme.setUpdatedAt(now);
        theme.setReferenceId(GraviteeContext.getCurrentEnvironment());
        theme.setReferenceType(ThemeReferenceType.ENVIRONMENT.name());
        theme.setLogo(themeEntity.getLogo());
        theme.setLoader(themeEntity.getLoader());
        theme.setName(themeEntity.getName());
        theme.setDefinition(definitionMapper.writeValueAsString(themeEntity.getDefinition()));
        theme.setEnabled(themeEntity.isEnabled());
        theme.setBackgroundImage(themeEntity.getBackgroundImage());
        theme.setOptionalLogo(themeEntity.getOptionalLogo());
        return theme;
    }

    private ThemeEntity convert(final Theme theme) throws RuntimeException {
        ThemeDefinitionMapper definitionMapper = new ThemeDefinitionMapper();
        final ThemeEntity themeEntity = new ThemeEntity();
        themeEntity.setId(theme.getId());
        themeEntity.setName(theme.getName());
        try {
            themeEntity.setDefinition(definitionMapper.readDefinition(theme.getDefinition()));
        } catch (IOException e) {
            LOGGER.error("Cannot read definition of theme " + theme.getId() + " definition:" + theme.getDefinition());
        }
        themeEntity.setCreatedAt(theme.getCreatedAt());
        themeEntity.setUpdatedAt(theme.getUpdatedAt());
        themeEntity.setEnabled(theme.isEnabled());
        themeEntity.setLogo(theme.getLogo());
        themeEntity.setLoader(theme.getLoader());
        themeEntity.setBackgroundImage(theme.getBackgroundImage());
        themeEntity.setOptionalLogo(theme.getOptionalLogo());
        return themeEntity;
    }


    public static class ThemeDefinitionMapper extends ObjectMapper {

        public ThemeDefinition readDefinition(String themeDefinition) throws IOException {
            return this.readValue(themeDefinition, ThemeDefinition.class);
        }

        public ThemeDefinition merge(final String base, final String override) throws IOException {
            final ThemeDefinition overrideDefinitionFinal = this.readDefinition(override);
            final ThemeDefinition overrideDefinition = this.readDefinition(base);
            ThemeDefinition mergedDefinition = this.readerForUpdating(overrideDefinition).readValue(base);

            List<ThemeComponentDefinition> componentsData = mergedDefinition
                    .getData()
                    .stream()
                    .map(component -> {

                        List<ThemeCssDefinition> cssMerged = component.getCss()
                                .stream()
                                .map(css -> {
                                    ThemeCssDefinition customCss = getThemeCssDefinition(overrideDefinitionFinal, component.getName(), css.getName());
                                    if (customCss != null) {
                                        css.setValue(customCss.getValue());
                                    }
                                    return css;
                                })
                                .collect(Collectors.toList());
                        component.setCss(cssMerged);
                        return component;
                    })
                    .collect(Collectors.toList());

            mergedDefinition.setData(componentsData);
            return mergedDefinition;
        }

        public ThemeComponentDefinition getThemeComponentDefinition(ThemeDefinition themeDefinition, String name) {
            return themeDefinition.getData()
                    .stream()
                    .filter(themeComponentDefinition -> name.equals(themeComponentDefinition.getName()))
                    .findFirst()
                    .orElse(null);
        }

        public ThemeCssDefinition getThemeCssDefinition(ThemeDefinition themeDefinition, String name, String cssName) {
            ThemeComponentDefinition componentDefinition = getThemeComponentDefinition(themeDefinition, name);
            if (componentDefinition != null) {
                return componentDefinition.getCss()
                        .stream()
                        .filter(css -> cssName.equals(css.getName())).findFirst().orElse(null);
            }
            return null;
        }

    }

}
