package org.telekit.base.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.telekit.base.domain.exception.TelekitException;
import org.telekit.base.i18n.BaseMessageKeys;
import org.telekit.base.i18n.Messages;
import org.telekit.base.service.Serializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class JacksonXmlSerializer<T> implements Serializer<T> {

    private final XmlMapper mapper;
    private final TypeReference<T> typeRef;

    public JacksonXmlSerializer(XmlMapper mapper, TypeReference<T> typeRef) {
        this.mapper = mapper;
        this.typeRef = typeRef;
    }

    @Override
    public void serialize(OutputStream outputStream, T value) {
        try {
            mapper.writeValue(outputStream, value);
        } catch (IOException e) {
            throw new TelekitException(Messages.get(BaseMessageKeys.MGG_UNABLE_TO_SAVE_DATA_TO_FILE));
        }
    }

    @Override
    public T deserialize(InputStream inputStream) {
        try {
            return mapper.readValue(inputStream, typeRef);
        } catch (IOException e) {
            throw new TelekitException(Messages.get(BaseMessageKeys.MGG_UNABLE_TO_LOAD_DATA_FROM_FILE));
        }
    }
}
