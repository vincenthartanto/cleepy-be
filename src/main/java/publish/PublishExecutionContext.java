package publish;

import clip.Clip;
import social.SocialConnection;

record PublishExecutionContext(
        PublishJob job,
        Clip clip,
        SocialConnection connection) {
}
