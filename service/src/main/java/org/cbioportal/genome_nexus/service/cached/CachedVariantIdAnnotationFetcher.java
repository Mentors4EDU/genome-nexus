package org.cbioportal.genome_nexus.service.cached;

import com.mongodb.DBObject;
import org.cbioportal.genome_nexus.model.VariantAnnotation;
import org.cbioportal.genome_nexus.persistence.VariantAnnotationRepository;
import org.cbioportal.genome_nexus.persistence.internal.VariantAnnotationRepositoryImpl;
import org.cbioportal.genome_nexus.service.exception.ResourceMappingException;
import org.cbioportal.genome_nexus.service.transformer.ExternalResourceTransformer;
import org.cbioportal.genome_nexus.service.remote.VEPIdDataFetcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CachedVariantIdAnnotationFetcher extends BaseCachedExternalResourceFetcher<VariantAnnotation, VariantAnnotationRepository>
{
    @Autowired
    public CachedVariantIdAnnotationFetcher(ExternalResourceTransformer<VariantAnnotation> transformer,
                                            VariantAnnotationRepository repository,
                                            VEPIdDataFetcher fetcher,
                                            @Value("${vep.max_page_size:200}") Integer maxPageSize)
    {
        super(VariantAnnotationRepositoryImpl.COLLECTION,
            repository,
            VariantAnnotation.class,
            fetcher,
            transformer,
            maxPageSize);
    }

    @Override
    protected Boolean isValidId(String id) 
    {
        return id.matches("rs\\d+") || id.matches("COSM\\d+");
    }

    @Override
    protected String extractId(VariantAnnotation instance)
    {
        return instance.getVariantId();
    }

    @Override
    protected String extractId(DBObject dbObject)
    {
        return (String)dbObject.get("input");
    }

    @Override
    public List<VariantAnnotation> fetchAndCache(List<String> ids) throws ResourceMappingException
    {
        List<VariantAnnotation> annotations = super.fetchAndCache(ids);
        return annotations;
    }

    @Override
    protected Object buildRequestBody(Set<String> ids)
    {
        HashMap<String, Object> requestBody = new HashMap<>();

        requestBody.put("ids", ids);

        return requestBody;
    }

    protected void saveToDb(DBObject value)
    {
        List<DBObject> dbObjects = this.transformer.transform(value);

        for (DBObject dbObject: dbObjects) {
            dbObject.put("_id", this.extractId(dbObject));
        }

        this.repository.saveDBObjects(this.collection, dbObjects);
    }
}
