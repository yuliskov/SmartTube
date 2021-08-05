package com.liskovsoft.smartyoutubetv2.tv;

import android.media.MediaDescription;
import android.net.Uri;
import android.os.Parcel;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class VideoTest {

    @Test
    public void VideoParceableTest() {
        List<Video> testVideoList = new ArrayList<>();
        testVideoList.add(new Video.VideoBuilder()
                .title("Dragon Movie")
                .description("A delightful kids movie")
                .studio("Dream Movies")
                .id((long) (Math.random()*100L))
                .build());
        testVideoList.add(new Video.VideoBuilder()
                .title("Grimm Fairy Tales")
                .description("A live action adaptation of a classic cartoon")
                .studio("Hollywood Studios")
                .cardImageUrl("http://example.com/grim_poster.png")
                .bgImageUrl("http://example.com/grim_bg.png")
                .build());
        testVideoList.add(new Video.VideoBuilder()
                .title("Kyle")
                .description("A live action adaptation of an old story")
                .studio("Wolf")
                .cardImageUrl("http://example.com/kyle_poster.png")
                .bgImageUrl("http://example.com/kyle_bg.png")
                .id(200)
                .build());
        testVideoList.add(new Video.VideoBuilder()
            .buildFromMediaDesc(new MediaDescription.Builder()
                    .setTitle("The Adventures of Albert the Raccoon")
                    .setDescription("Albert and friends travel around Arnoria completing quests")
                    .setMediaId("535")
                    .setSubtitle("Fantasy Productions")
                    .setIconUri(Uri.parse("http://www.example.co.uk/static/images/raccoon_colour.jpg"))
                    .build()));

        for (Video testVideo : testVideoList) {
            Parcel testVideoParcel = Parcel.obtain();
            testVideo.writeToParcel(testVideoParcel, 0);
            Video testVideoClone = Video.CREATOR.createFromParcel(testVideoParcel);

            assert testVideo.id == testVideoClone.id;
            assert testVideo.title.equals(testVideoClone.title);
            assert testVideo.description.equals(testVideoClone.description);
            assert testVideo.category.equals(testVideoClone.category);
            assert testVideo.author.equals(testVideoClone.author);
            assert testVideo.bgImageUrl.equals(testVideoClone.bgImageUrl);
            assert testVideo.cardImageUrl.equals(testVideoClone.cardImageUrl);
            assert testVideo.toString().equals(testVideoClone.toString());
        }
    }
}
