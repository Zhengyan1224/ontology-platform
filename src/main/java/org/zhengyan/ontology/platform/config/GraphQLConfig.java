package org.zhengyan.ontology.platform.config;

import graphql.language.StringValue;
import graphql.schema.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class GraphQLConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiring -> wiring.scalar(jsonScalar());
    }

    private GraphQLScalarType jsonScalar() {
        return GraphQLScalarType.newScalar()
                .name("JSON")
                .description("JSON scalar type")
                .coercing(new Coercing<Object, Object>() {
                    @Override
                    public Object serialize(Object input) {
                        return input;
                    }

                    @Override
                    public Object parseValue(Object input) {
                        return input;
                    }

                    @Override
                    public Object parseLiteral(Object input) {
                        if (input instanceof StringValue sv) {
                            return sv.getValue();
                        }
                        return input;
                    }
                })
                .build();
    }
}
