package publish;

import io.smallrye.mutiny.Uni;
import social.SocialPlatform;

public interface PublisherAdapter {

    SocialPlatform platform();

    Uni<PublishSubmissionResult> submit(PublishExecutionContext context);

    Uni<PublishStatusResult> refresh(PublishExecutionContext context);
}
