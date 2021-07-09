package org.telekit.base.preferences.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.telekit.base.BaseSetup;
import org.telekit.base.domain.security.SecuredData;
import org.telekit.base.preferences.internal.Security;
import org.telekit.base.util.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(BaseSetup.class)
public class SecurityTest {

    @Test
    void testSerialization() throws JsonProcessingException {
        String password = "qwerty";

        SecuredData dataOrig = SecuredData.fromString(password);
        Security securityOrig = new Security();
        securityOrig.setVaultPassword(dataOrig);

        YAMLMapper mapper = Mappers.createYamlMapper();
        String yaml = mapper.writeValueAsString(securityOrig);

        Security securityDest = mapper.readValue(yaml, Security.class);
        assertThat(securityDest.getVaultPassword().getData()).isEqualTo(dataOrig.getData());
    }
}