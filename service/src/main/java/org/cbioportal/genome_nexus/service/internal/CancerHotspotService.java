/*
 * Copyright (c) 2016 Memorial Sloan-Kettering Cancer Center.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF MERCHANTABILITY OR FITNESS
 * FOR A PARTICULAR PURPOSE. The software and documentation provided hereunder
 * is on an "as is" basis, and Memorial Sloan-Kettering Cancer Center has no
 * obligations to provide maintenance, support, updates, enhancements or
 * modifications. In no event shall Memorial Sloan-Kettering Cancer Center be
 * liable to any party for direct, indirect, special, incidental or
 * consequential damages, including lost profits, arising out of the use of this
 * software and its documentation, even if Memorial Sloan-Kettering Cancer
 * Center has been advised of the possibility of such damage.
 */

/*
 * This file is part of cBioPortal Genome Nexus.
 *
 * cBioPortal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.cbioportal.genome_nexus.service.internal;

import org.cbioportal.genome_nexus.model.Hotspot;
import org.cbioportal.genome_nexus.model.TranscriptConsequence;
import org.cbioportal.genome_nexus.service.HotspotService;
import org.cbioportal.genome_nexus.service.exception.CancerHotspotsWebServiceException;
import org.cbioportal.genome_nexus.service.exception.JsonMappingException;
import org.cbioportal.genome_nexus.util.Numerical;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Selcuk Onur Sumer
 */
@Service
public class CancerHotspotService implements HotspotService
{
    private HotspotCache cache;

    private String hotspotsURL;
    @Value("${hotspots.url}")
    public void setHotspotsURL(String hotspotsURL) { this.hotspotsURL = hotspotsURL; }

    private final ExternalResourceTransformer externalResourceTransformer;

    @Autowired
    public CancerHotspotService(ExternalResourceTransformer externalResourceTransformer)
    {
        this.externalResourceTransformer = externalResourceTransformer;
    }

    @Override
    public List<Hotspot> getHotspots(String transcriptId) throws CancerHotspotsWebServiceException
    {
        // get it by using the specific API
//        try
//        {
//            return Transformer.mapJsonToInstance(getHotspotsJSON(transcriptId), Hotspot.class);
//        }
//        catch (IOException e)
//        {
//            e.printStackTrace();
//            return Collections.emptyList();
//        }

        //use cache instead
        return getHotspotsFromCache(transcriptId);
    }

    @Override
    public List<Hotspot> getHotspots(TranscriptConsequence transcript) throws CancerHotspotsWebServiceException
    {
        List<Hotspot> hotspots = new ArrayList<>();

        for (Hotspot hotspot : this.getHotspots(transcript.getTranscriptId()))
        {
            // only include hotspots overlapping the protein change position
            // of the current transcript
            if (Numerical.overlaps(hotspot.getResidue(),
                                   transcript.getProteinStart(),
                                   transcript.getProteinEnd()))
            {
                // TODO use a JSON view instead of copying fields to another model?
                // we have data duplication here...
                hotspot.setGeneId(transcript.getGeneId());
                hotspot.setProteinStart(transcript.getProteinStart());
                hotspot.setProteinEnd(transcript.getProteinEnd());

                hotspots.add(hotspot);
            }
        }

        return hotspots;
    }

    @Override
    public List<Hotspot> getHotspots() throws CancerHotspotsWebServiceException
    {
        try
        {
            return this.externalResourceTransformer.transform(getHotspotsJSON(null), Hotspot.class);
        }
        catch (JsonMappingException e)
        {
            throw new CancerHotspotsWebServiceException(e.getMessage());
        }
        catch (HttpClientErrorException e)
        {
            throw new CancerHotspotsWebServiceException(e.getResponseBodyAsString(), e.getStatusCode());
        }
        catch (ResourceAccessException e)
        {
            throw new CancerHotspotsWebServiceException(e.getMessage());
        }
    }

    private String getHotspotsJSON(String variables)
    {
        String uri = hotspotsURL;

        if (variables != null &&
            variables.length() > 0)
        {
            // TODO partially hardcoded API URI!
            uri += "/byTranscript/" + variables;
        }

        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(uri, String.class);
    }

    private List<Hotspot> getHotspotsFromCache(String transcriptId) throws CancerHotspotsWebServiceException
    {
        // if null: not initialized yet
        if (cache == null)
        {
            List<Hotspot> hotspots = this.getHotspots();

            if (hotspots.size() > 0)
            {
                this.cache = new HotspotCache(hotspots);
            }
        }

        List<Hotspot> hotspots = null;

        // still null: error at initialization
        if (cache != null) {
            hotspots = cache.findByTranscriptId(transcriptId);
        }

        return hotspots;
    }
}