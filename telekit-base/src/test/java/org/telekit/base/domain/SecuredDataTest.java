package org.telekit.base.domain;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.telekit.base.BaseSetup;
import org.telekit.base.domain.security.SecuredData;
import org.telekit.base.util.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(BaseSetup.class)
public class SecuredDataTest {

    @Test
    void testSerialization() throws Exception {
        String password = "qwerty";

        SecuredData orig = new SecuredData();
        orig.setData(password.getBytes());

        YAMLMapper mapper = Mappers.createYamlMapper();
        String yaml = mapper.writeValueAsString(orig);

        SecuredData dest = mapper.readValue(yaml, SecuredData.class);
        assertThat(dest.getData()).isEqualTo(password.getBytes());
    }
}