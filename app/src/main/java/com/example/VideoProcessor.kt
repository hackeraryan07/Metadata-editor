package com.example

import org.mp4parser.IsoFile
import org.mp4parser.boxes.apple.AppleCoverBox
import org.mp4parser.boxes.apple.AppleItemListBox
import org.mp4parser.boxes.iso14496.part12.MetaBox
import org.mp4parser.boxes.iso14496.part12.MovieBox
import org.mp4parser.boxes.iso14496.part12.UserDataBox
import org.mp4parser.boxes.iso14496.part12.HandlerBox
import org.mp4parser.boxes.iso14496.part12.StaticChunkOffsetBox
import org.mp4parser.boxes.iso14496.part12.ChunkOffset64BitBox
import org.mp4parser.boxes.iso14496.part12.SampleTableBox
import org.mp4parser.boxes.iso14496.part12.TrackBox
import org.mp4parser.boxes.iso14496.part12.MediaBox
import org.mp4parser.boxes.iso14496.part12.MediaInformationBox
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

object VideoProcessor {

    fun setCover(inputVideoPath: String, coverBytes: ByteArray, isJpg: Boolean, outputVideoPath: String) {
        val isoFile = IsoFile(inputVideoPath)

        var moov: MovieBox? = null
        for (box in isoFile.boxes) {
            if (box is MovieBox) {
                moov = box
                break
            }
        }
        
        if (moov == null) {
            isoFile.close()
            throw Exception("No moov box found in video.")
        }

        // Keep track of the moov box old size
        val oldMoovSize = moov.size

        var udta: UserDataBox? = moov.getBoxes(UserDataBox::class.java).firstOrNull()
        if (udta == null) {
            udta = UserDataBox()
            moov.addBox(udta)
        }

        var meta: MetaBox? = udta.getBoxes(MetaBox::class.java).firstOrNull()
        if (meta == null) {
            meta = MetaBox()
            val hdlr = HandlerBox()
            hdlr.name = "Apple mark"
            hdlr.handlerType = "mdir"
            meta.addBox(hdlr)
            udta.addBox(meta)
        }

        var ilst: AppleItemListBox? = meta.getBoxes(AppleItemListBox::class.java).firstOrNull()
        if (ilst == null) {
            ilst = AppleItemListBox()
            meta.addBox(ilst)
        }

        // Remove existing cover if any
        val newBoxes = ArrayList<org.mp4parser.Box>()
        for (box in ilst.boxes) {
            if (box !is AppleCoverBox) {
                newBoxes.add(box)
            }
        }
        ilst.boxes = newBoxes

        val cover = AppleCoverBox()
        if (isJpg) {
            cover.setJpg(coverBytes)
        } else {
            cover.setPng(coverBytes)
        }
        ilst.addBox(cover)

        val newMoovSize = moov.getSize()
        val sizeDiff = newMoovSize - oldMoovSize

        // If moov is placed BEFORE mdat, increasing moov size pushes mdat down by sizeDiff.
        // Therefore, we MUST shift all chunk offsets by sizeDiff.
        // Let's determine if moov is before mdat
        var isMoovBeforeMdat = false
        for (box in isoFile.boxes) {
            if (box is MovieBox) {
                isMoovBeforeMdat = true
            } else if (box.type == "mdat") {
                // If we encounter mdat and moov already happened, moov is before mdat
                break
            }
            if (box.type == "mdat" && !isMoovBeforeMdat) {
                // mdat is before moov
                break
            }
        }

        if (isMoovBeforeMdat && sizeDiff > 0) {
            for (trackBox in moov.getBoxes(TrackBox::class.java)) {
                val mdia = trackBox.getBoxes(MediaBox::class.java).firstOrNull() ?: continue
                val minf = mdia.getBoxes(MediaInformationBox::class.java).firstOrNull() ?: continue
                val stbl = minf.getBoxes(SampleTableBox::class.java).firstOrNull() ?: continue

                val stco = stbl.getBoxes(StaticChunkOffsetBox::class.java).firstOrNull()
                if (stco != null) {
                    val offsets = stco.chunkOffsets
                    for (i in offsets.indices) {
                        offsets[i] = offsets[i] + sizeDiff
                    }
                    stco.chunkOffsets = offsets
                }

                val co64 = stbl.getBoxes(ChunkOffset64BitBox::class.java).firstOrNull()
                if (co64 != null) {
                    val offsets = co64.chunkOffsets
                    for (i in offsets.indices) {
                        offsets[i] = offsets[i] + sizeDiff
                    }
                    co64.chunkOffsets = offsets
                }
            }
        }

        val pOut = File(outputVideoPath)
        if (pOut.exists()) {
            pOut.delete()
        }
        val outRac = RandomAccessFile(pOut, "rw")
        val fc = outRac.channel
        
        // Write the modified file out manually based on IsoFile's boxes.
        isoFile.getBox(fc)
        
        fc.close()
        outRac.close()
        isoFile.close()
    }
}
