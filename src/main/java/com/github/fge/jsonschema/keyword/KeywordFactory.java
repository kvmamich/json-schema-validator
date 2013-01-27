/*
 * Copyright (c) 2012, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.fge.jsonschema.keyword;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.metaschema.MetaSchema;
import com.github.fge.jsonschema.report.Domain;
import com.github.fge.jsonschema.report.Message;
import com.github.fge.jsonschema.report.ValidationReport;
import com.github.fge.jsonschema.syntax.SyntaxValidator;
import com.github.fge.jsonschema.util.NodeType;
import com.github.fge.jsonschema.validator.ValidationContext;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

/**
 * Factory to provide a set of {@link KeywordValidator} instances for a given
 * schema
 *
 * <p>This class is only called once the schemas has been deemed valid, that is,
 * the following is true:</p>
 *
 * <ul>
 *     <li>the JSON document is not a JSON reference (ie, if it was, it has been
 *     resolved successfully);</li>
 *     <li>it is syntactically valid (see {@link SyntaxValidator}).</li>
 * </ul>
 *
 * <p>Note that failing to build a keyword validator is considered a fatal error
 * and stops validation immediately (reminder: keyword validators are built via
 * reflection).</p>
 */
public final class KeywordFactory
{
    /**
     * Our existing set of keyword validators
     */
    private final Map<String, Class<? extends KeywordValidator>> validators;

    public KeywordFactory(final MetaSchema metaSchema)
    {
        validators = metaSchema.getValidators();
    }

    /**
     * Return the set of validators for a particular schema
     *
     * @param schema the schema as a {@link JsonNode}
     * @return the set of validators
     */
    public Set<KeywordValidator> getValidators(final JsonNode schema)
    {
        final ImmutableSet.Builder<KeywordValidator> builder
            = ImmutableSet.builder();

        final Set<String> set = Sets.newHashSet(schema.fieldNames());

        set.retainAll(validators.keySet());

        KeywordValidator validator;

        for (final String keyword: set) {
            validator = buildValidator(validators.get(keyword), schema);
            if (!validator.alwaysTrue())
                builder.add(validator);
        }

        return builder.build();
    }

    /**
     * Build one validator
     *
     * <p>This is done by reflection. Remember that the contract is to have a
     * constructor which takes a {@link JsonNode} as an argument.
     * </p>
     *
     * <p>If instantiation fails for whatever reason, an "invalid validator" is
     * returned which always fails.</p>
     *
     * @see #invalidValidator(Class, Exception)
     *
     * @param c the keyword validator class
     * @param schema the schema
     * @return the instantiated keyword validator
     */
    private static KeywordValidator buildValidator(
        final Class<? extends KeywordValidator> c, final JsonNode schema)
    {
        final Constructor<? extends KeywordValidator> constructor;

        try {
            constructor = c.getConstructor(JsonNode.class);
        } catch (NoSuchMethodException e) {
            return invalidValidator(c, e);
        }

        try {
            return constructor.newInstance(schema);
        } catch (InstantiationException e) {
            return invalidValidator(c, e);
        } catch (IllegalAccessException e) {
            return invalidValidator(c, e);
        } catch (InvocationTargetException e) {
            return invalidValidator(c, e);
        }
    }

    /**
     * Build an invalid validator in the event of instantiation failure
     *
     * @param e the exception raised by the instantiation attempt
     * @return a keyword validator which always fails
     */
    private static KeywordValidator invalidValidator(
        final Class<? extends KeywordValidator> c, final Exception e)
    {
        final String className = c.getName();

        return new KeywordValidator(className, NodeType.values())
        {
            @Override
            protected void validate(final ValidationContext context,
                final ValidationReport report, final JsonNode instance)
            {
                final Message.Builder msg = Domain.VALIDATION.newMessage()
                    .setMessage("cannot build validator").setKeyword(className)
                    .addInfo("exception", e.getClass().getName())
                    .addInfo("exceptionMessage", e.getMessage()).setFatal(true);
                report.addMessage(msg.build());
            }

            @Override
            public String toString()
            {
                return className;
            }
        };
    }
}