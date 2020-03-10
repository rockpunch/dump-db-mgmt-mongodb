package io.dsub.dumpdbmgmt.config;

import io.dsub.dumpdbmgmt.batch.CustomStaxEventItemReader;
import io.dsub.dumpdbmgmt.entity.Artist;
import io.dsub.dumpdbmgmt.entity.Label;
import io.dsub.dumpdbmgmt.entity.MasterRelease;
import io.dsub.dumpdbmgmt.entity.Release;
import io.dsub.dumpdbmgmt.xmlobj.XmlArtist;
import io.dsub.dumpdbmgmt.xmlobj.XmlLabel;
import io.dsub.dumpdbmgmt.xmlobj.XmlMaster;
import io.dsub.dumpdbmgmt.xmlobj.XmlRelease;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;

/**
 * JobConfiguration which contains entire job procedure.
 * NOTE: orders of steps DO MATTER.
 * Read each processors beforehand to change any orders if necessary.
 *
 * todo: remove boilerplate codes.
 */

@Configuration
public class JobConfig {

    TaskExecutor taskExecutor;
    JobBuilderFactory jobBuilderFactory;
    StepBuilderFactory stepBuilderFactory;
    PlatformTransactionManager tm;

    public JobConfig(
            JobBuilderFactory jobBuilderFactory,
            StepBuilderFactory stepBuilderFactory,
            @Qualifier("transactionManager") PlatformTransactionManager tm,
            @Qualifier("batchTaskExecutor") TaskExecutor taskExecutor) {

        this.jobBuilderFactory = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
        this.tm = tm;
        this.taskExecutor = taskExecutor;
    }

    // A one-shot job operate on following:
    //
    //  1. First four steps
    //  Persist each individual entities (release, artist, label, master-release)
    //  represented as each steps' names with corresponding entities.
    //    NOTE:: masterReleaseStep is exception as it requires releases and artists, hence
    //           each row will be persisted WHILE having document refs.
    //
    //  2. Second three steps
    //  Referencing corresponded entities (i.e. label and release, artist with release...)
    //  These steps MUST be put after 1st step, otherwise, docs will referencing non-existing documents,
    //  which will obviously lead to batch failure.
    //

    @Bean
    public Job job(
            @Qualifier("releaseStep") Step releaseStep,
            @Qualifier("labelStep") Step labelStep,
            @Qualifier("artistStep") Step artistStep,
            @Qualifier("masterReleaseStep") Step masterReleaseStep) {
        return jobBuilderFactory.get(LocalDateTime.now().toString())
//                .start(artistStep)
//                .next(labelStep)
//                .next(masterReleaseStep)
                .start(releaseStep)
                .build();
    }

    @Bean("releaseStep")
    public Step releaseStep(CustomStaxEventItemReader<XmlRelease> releaseReader,
                            @Qualifier("releaseProcessor") ItemProcessor<XmlRelease, Release> releaseProcessor,
                            RepositoryItemWriter<Release> releaseWriter) {
        return stepBuilderFactory.get("releaseStep")
                .<XmlRelease, Release>chunk(3000)
                .reader(releaseReader)
                .processor(releaseProcessor)
                .writer(releaseWriter)
                .taskExecutor(taskExecutor)
                .transactionManager(tm)
                .build();
    }

    @Bean("labelStep")
    public Step labelStep(CustomStaxEventItemReader<XmlLabel> labelReader,
                          @Qualifier("labelProcessor") ItemProcessor<XmlLabel, Label> labelProcessor,
                          RepositoryItemWriter<Label> labelWriter) {
        return stepBuilderFactory.get("labelStep")
                .<XmlLabel, Label>chunk(3000)
                .reader(labelReader)
                .processor(labelProcessor)
                .writer(labelWriter)
                .taskExecutor(taskExecutor)
                .transactionManager(tm)
                .build();
    }

    @Bean("artistStep")
    public Step artistStep(CustomStaxEventItemReader<XmlArtist> artistReader,
                           @Qualifier("artistProcessor") ItemProcessor<XmlArtist, Artist> artistProcessor,
                           RepositoryItemWriter<Artist> artistWriter) {
        return stepBuilderFactory.get("artistStep")
                .<XmlArtist, Artist>chunk(3000)
                .reader(artistReader)
                .processor(artistProcessor)
                .writer(artistWriter)
                .taskExecutor(taskExecutor)
                .transactionManager(tm)
                .build();
    }

    @Bean("masterReleaseStep")
    public Step masterReleaseStep(CustomStaxEventItemReader<XmlMaster> reader,
                                  @Qualifier("masterReleaseProcessor") ItemProcessor<XmlMaster, MasterRelease> processor,
                                  RepositoryItemWriter<MasterRelease> writer) {
        return stepBuilderFactory.get("masterReleaseStep")
                .<XmlMaster, MasterRelease>chunk(3000)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .taskExecutor(taskExecutor)
                .transactionManager(tm)
                .build();
    }
}
